/*
PLANS:

* support array of associations as incoming dict!
* keep assocArray as instVar and sync/convert the other to a dict
* make a directory cache of filenames -> devicenames as reported
* update only when files newer than cache were added

*/

MKtlDesc {
	classvar <defaultFolder, <folderName = "MKtlDescriptions";
	classvar <fileExt = ".desc.scd";
	classvar <descFolders;
	classvar <allDescs;
	classvar <cachePath, <fileToIDDict;

	classvar <>isElementTestFunc;

	var <name, <fullDesc, <path, <>elementsAssocArray;

	*initClass {
		defaultFolder = this.filenameSymbol.asString.dirname.dirname
			+/+ folderName;
		descFolders = List[defaultFolder];
		allDescs =();
		isElementTestFunc = { |el| el.isKindOf(Dictionary) and: { el[\spec].notNil } };

		cachePath =defaultFolder +/+ "_descFileLookup.cache.scd";

		this.loadCache;
	}

	// Files admin methods:

	*addFolder { |path, name = (folderName)|
		var folderPath = path +/+ name;
		var foundFolder = pathMatch(folderPath);

		if (descFolders.includesEqual(folderPath)) { ^this };
		descFolders.add(folderPath);
		if (foundFolder.notEmpty) {
			"MKtlDesc found and added folder: %\n".postf(foundFolder.cs);
		} {
			"// MKtlDesc added a nonexistent folder: %\n.".postf(name.cs);
			"// you can create it with:"
			"\n File.mkdir(\"%\".standardizePath);\n".postf(folderPath);
		}
	}

	*openFolder { |index = 0|
		descFolders[index].openOS;
	}

	*findFile { |filename = "*", folderIndex, postFound = false|
		var foundPaths, foldersToLoadFrom, plural = 0;
		folderIndex = folderIndex ?? { (0 .. descFolders.size-1) };
		foldersToLoadFrom = descFolders[folderIndex.asArray].select(_.notNil);

		foundPaths = descFolders.collect { |dir|
			(dir +/+ filename ++ fileExt).pathMatch
		}.flatten(1);

		if (postFound) {
			plural = if (foundPaths.size == 1, "s", "");
			"\n*** MKtlDesc.findFile found % file% for'%': ***\n"
			.postf(foundPaths.size, plural, filename);
			foundPaths.printcsAll; "".postln;
		}

		^foundPaths
	}

	// convenience only
	*loadDescs { |filename = "*", folderIndex|
		var paths = this.findFile(filename, folderIndex);
		paths.do {|path|
			this.fromPath(path);
		};
		"\n// MKtlDesc loaded % description files - see:"
		"\nMKtlDesc.allDescs;\n".postf(paths.size);
	}

	*postLoaded {
		"\n*** MKtlDesc - loaded descs: ***".postln;
		allDescs.keys.asArray.sort.do { |key|
			"% // %\n".postf(allDescs[key], allDescs[key].idInfo);
		};
		"******\n".postln;
	}

	*at { |descName|
		^allDescs[descName]
	}

	// create lookup dicts for filename -> idInfo and back
	// this will allow loading just the file needed, not all files.

	*idInfoFor { |filename| ^fileToIDDict.at(filename) }

	*filenameFor { |idInfo| ^fileToIDDict.findKeyForValue(idInfo) }

	*writeCache {
		var fileToIDDictToWrite = Dictionary.new;

		MKtlDesc.loadDescs;

		MKtlDesc.allDescs.collect { |desc|
			var filename = desc.fullDesc.filename;
			var idInfo = desc.fullDesc.idInfo;
			fileToIDDictToWrite.put(filename, idInfo);
		};


		File.open(cachePath, "w").write(fileToIDDictToWrite.cs).close;
		/*
		MKtlDesc.writeCache;
		MKtlDesc.loadCache;
		MKtlDesc.fileToIDDict;
		*/

	}

	*loadCache {
		fileToIDDict = cachePath.load;
	}

	*updateCache {
		// check if any files have changed,
		// and if so, make a new cache file.
	}

	// integrity checks

	// according to current definition,
	// \idInfo, \protocol, \description are required;
	// can add more tests here,
	// e.g. check whether description is wellformed
	*isValidDescDict { |dict|
		^dict.isKindOf(Dictionary)
		or: { dict.isAssociationArray
		and: { dict[\idInfo].notNil
		and: { dict[\protocol].notNil
		and: { dict[\description].notNil
	//	and: { this.checkElementsDesc(dict) }
					}
				}
			}
		}
	}

	// write tests for this later
	*checkElementsDesc { |desc|
		var midiMsgTypes, missing;
		if (this.protocol == \midi) {
			#midiMsgTypes, missing = MKtlDesc.checkMIDIMsgTypes;
		};
		if (missing.size > 0) {
			warn("" + this + ": has no midiMsgType for elements: %".format(missing));
		};
		^(missing > 0)
	}


	checkMIDIMsgTypes {
		var types = Set.new, type, missing = [];
		this.elementsDesc.do { |el|
			var type = el[\midiMsgType];
			if (type.isNil) {
				missing = missing.add(el.name);
			} {
				types.add(type);
			};
		};
		^(msgTypesUsed: types, msgTypesMissingIn: missing);
	}


	// creation methods
	*fromFileName { |filename, folderIndex|
		var paths = this.findFile(filename, folderIndex, false);
		if (paths.isEmpty) {
			warn("MktlDesc: could not find desc with filename %.\n"
				.format(filename));
			^nil;
		};
		if (paths.size > 1) {
			warn("MktlDesc: found multiple files, loading only first: %.\n"
				.format(paths[0].basename));
		};
		^this.fromPath(paths[0]);
	}

	*fromPath { |path|
		var desc = path.load;
		if (desc.isNil) {
			warn("MktlDesc: could not load desc from path %.\n"
				.format(path));
			^nil;
		};

		if (this.isValidDescDict(desc).not) {
			warn("MktlDesc: desc loaded from path % is not valid.\n"
				.format(path));
			^nil
		};
		// got here, should work now
		desc.path = path;
		desc.filename = path.basename.drop(fileExt.size.neg);
		^this.fromDict(desc);
	}

	*fromDict { |dict|
		if (this.isValidDescDict(dict).not) {
			warn("MKtlDesc - dict is not a valid description: %"
				.format(dict));
			^nil
		};
		^super.new.fullDesc_(dict);
	}

	*new { |name|
		var foundObj = this.at(name ?? { name.asSymbol });
		if (foundObj.notNil) {
			^foundObj;
		};

		if (name.notNil) {
			^this.fromFileName(name);
		};
		// for making it from dict
		^super.new;
	}

	// initialisation/preparation
	fullDesc_ { |inDesc|
		if (this.class.isValidDescDict(inDesc).not) {
			warn("MKtlDesc: dict is not a valid desc,"
				" so cannot make elements.");
			^this
		};
		// "fullDesc: inDesc is ok, filename: %\npath: %\n"
		// .postf(inDesc.filename, inDesc.path);

		fullDesc = inDesc;
		path = path ?? { fullDesc[\path]; };

		// make elements in both forms
		this.prMakeElemColls(fullDesc);
		this.inferName;
	//	this.resolveDescEntriesForPlatform;
	}

	inferName { |inname, force = false|
		var filename = fullDesc[\filename];

		if (name.notNil and: force.not) {
			^this
		};

		name = inname ?? { fullDesc[\filename] ??
			{ if (path.notNil) { path.basename.drop(fileExt.size.neg); };
		} };

		if (name.isNil) {
			warn("MKtlDesc: could not create valid name, so desc remains\n"
				"unnamed, and will not show up in MKtlDesc.allDescs.");
		} {
			name = name.asSymbol;
			allDescs.put(name, this);
		};
	}

	prMakeElemColls { |inDesc|
		if (inDesc.isKindOf(Dictionary)) {
			elementsAssocArray = inDesc.asAssociations;
		};
		if (inDesc.isAssociationArray) {
			elementsAssocArray = inDesc;
			this.elementsDesc = inDesc.asDict.as(Event);
		};
	}

	openFile {
		if (path.notNil) {
			path.asString.openDocument
		} {
			inform("" ++ this + ".openFile: path was nil.");
		};
	}

	// access - keep all data in fullDesc only
	protocol { ^fullDesc[\protocol] }
	protocol_ { |type| ^fullDesc[\protocol] = type }

	idInfo { ^fullDesc[\idInfo] }
	idInfo_ { |type| ^fullDesc[\idInfo] = type }

	elementsDesc { ^fullDesc[\description] }
	elementsDesc_ { |type| ^fullDesc[\description] = type }

	deviceFilename {
		^path !? { path.basename.drop(fileExt.size.neg) }
	}

	postInfo { |postElements = false|
		("---\n//" + this + $:) .postln;
		"deviceFilename: %\n".postf(this.deviceFilename);
		"protocol: %\n".postf(this.protocol);
		"deviceIDString: %\n".postf(this.deviceIDString);
		"desc keys: %\n".postf(this.elementsDesc.keys);

		if (postElements) { this.postElements }
	}

	postElements {
		this.elementsDesc.traverseDo({ |el, deepKeys|
			deepKeys.size.do { $\t.post };
			deepKeys.postcs;
		}, (_.isKindOf(Dictionary)));
	}

	writeFile { |path|
		"! more than nice to have ! - not done yet.".postln;
	}

	storeArgs { ^[name] }
	printOn { |stream|
		stream << this.class.name << ".at(%)".format(name.cs);
	}

	// some keys may be platform-dependent, e.g.
	// (meaning: (osx: 23, linux: 42, win: 4711));
	// these are resolved for the platform used,
	// e.g. for linux: (meaning: 42)
	*resolveForPlatform { |dict|
		var platForms = [\osx, \linux, \win];
		var myPlatform = thisProcess.platform.name;

		dict.keysValuesDo { |dictkey, entry|
			var foundPlatformDep = false, foundval;
			if (entry.isKindOf(Dictionary)) {
				foundPlatformDep = entry.keys.sect(platForms).notEmpty;
			};
			if (foundPlatformDep) {
				foundval = entry[myPlatform];
				"MKtlDesc replacing: ".post;
				dict.put(*[dictkey, foundval].postln);
			};
		}
		^dict
	}

	// (-: just in case programming ;-)
	resolveDescEntriesForPlatform {
		this.class.resolveForPlatform(fullDesc);
		this.elementsDesc.keysValuesDo { |key, elemDesc|
			MKtlDesc.resolveForPlatform(elemDesc);
		};
		this.class.resolveForPlatform(this.elementsDesc);
	}


}
