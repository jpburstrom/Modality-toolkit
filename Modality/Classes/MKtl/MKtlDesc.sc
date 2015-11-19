/*
Questions:
* update only when files newer than cache were added
- it is really fast anyway, so just do on every startup? -
*/

MKtlDesc {

	classvar <defaultFolder, <folderName = "MKtlDescriptions";
	classvar <descExt = ".desc.scd", <compExt = ".comp.scd";
	classvar <parentExt = ".parentDesc.scd";
	classvar <descFolders;

	classvar <allDescs;
	classvar <cacheName = "_allDescs.cache.scd";
	classvar <fileToIDDict;

	classvar <webview;
	classvar <docURI = "http://modalityteam.github.io/controllers/";

	classvar <>isElemFunc;

	var <name, <fullDesc, <path;
	var <elementsDict;

	*initClass {
		defaultFolder = MKtlDesc.filenameSymbol.asString.dirname.dirname.dirname
		+/+ folderName;
		descFolders = List[defaultFolder];
		allDescs =();
		isElemFunc = { |el|
			el.isKindOf(Dictionary) and: { el[\elements].isNil }
		};

		fileToIDDict = Dictionary.new;

		this.loadCache;
	}

	// access to all
	*at { |descName|
		^allDescs[descName]
	}

	// WEB interface
	*web { |ctlname = ""|
		webview = webview ?? { WebView() };
		webview.front.url_((docURI +/+ ctlname).postcs);
		webview.onClose_({ webview = nil });
	}

	docURI {|relative = false|
		if (relative) {
			^(name.asString ++ ".html");
		} {
			^(this.class.docURI +/+ name.asString ++ ".html");
		}
	}
	web {
		this.class.web(this.docURI(relative: true));
	}

	//----------- File admin methods:

	*addFolder { |path, name = (folderName)|
		var folderPath = path.standardizePath +/+ name;
		var foundFolder = pathMatch(folderPath);

		if (descFolders.includesEqual(folderPath)) { ^this };
		descFolders.add(folderPath);
		if (foundFolder.notEmpty) {
			"MKtlDesc found and added folder: %\n".postf(foundFolder.cs);
		} {
			"// MKtlDesc added a nonexistent folder: %\n.".postf(name.cs);
			"// you can create it with:"
			"\n File.mkdir(\"%\");\n".postf(folderPath);
		}
	}

	*openFolder { |index = 0|
		descFolders[index].openOS;
	}

	*findFile { |filename = "*", folderIndex, postFound = false, fileExt|
		var foundPaths, foldersToLoad, plural = 0;
		folderIndex = folderIndex ?? { (0 .. descFolders.size-1) };
		foldersToLoad = descFolders[folderIndex.asArray].select(_.notNil);

		fileExt = fileExt ? descExt;

		foundPaths = foldersToLoad.collect { |dir|
			(dir +/+ filename ++ fileExt).pathMatch
			// add depth of one folders
			++ (dir +/+ "*/" +/+ filename ++ fileExt).pathMatch
		}.flatten(1);

		if (postFound) {
			plural = if (foundPaths.size == 1, "s", "");
			"\n*** MKtlDesc found % file% for'%': ***\n"
			.postf(foundPaths.size, plural, filename);
			foundPaths.printcsAll; "".postln;
		}

		^foundPaths
	}

	*postLoadable {|folderIndex|
		folderIndex = folderIndex ?? { (0..descFolders.lastIndex) };
		descFolders[folderIndex.asArray].do { |folder, i|
			var found = this.findFile(folderIndex: i);
			"*** % descs in folder % - % : ***\n".postf(found.size, i, folder);
			found.do { |path|
				var loadStr = path.basename.drop(this.descExt.size.neg);
				("(" ++ loadStr.cs ++ ")").postln;
			};
			"****\n".postln;
		};
	}

	*loadDescs { |filename = "*", folderIndex, post = false|
		var paths = this.findFile(filename, folderIndex);
		var descs = paths.collect {|path|
			try { this.fromPath(path); };
		}.select(_.notNil);

		if (post) {
			"\n// MKtlDesc loaded % valid description files - see:"
			"\nMKtlDesc.allDescs;\n".postf(paths.size);
		};
		^descs
	}

	*postLoaded {
		"\n*** MKtlDesc - loaded descs: ***".postln;
		allDescs.keys(SortedList).do { |key|
			"% // %\n".postf(allDescs[key], allDescs[key].idInfo);
		};
		"******\n".postln;
	}

	// info
	*postStatus {
		MKtlDesc.loadDescs
		.sort { |a, b| a.fullDesc.status < b.fullDesc.status; }
		.do { |a| [a.fullDesc.status, a.name].postcs; };
	}


	// create lookup dicts for filename -> idInfo and back
	// this will allow loading just the file needed, not all files.

	*idInfoForFilename { |filename| ^fileToIDDict.at(filename) }

	*filenamesForIDInfo { |idInfo|
		^fileToIDDict.select { |info, filename|
			// may need better matching
			info == idInfo
		}.keys(Array).sort
	}

	*writeCache {
		var dictForFolder = Dictionary.new, file;

		descFolders.do { |folder, i|
			var descs = MKtlDesc.loadDescs(folderIndex: i);
			var path = folder +/+ cacheName;

			descs.collect { |desc|

				var filename = desc.fullDesc.filename;
				var idInfo = desc.fullDesc.idInfo;
				dictForFolder.put(filename, idInfo);
			};
			file = File.open(path, "w");
			if (file.isOpen) {
				file.write("Dictionary[\n");
				dictForFolder.sortedKeysValuesDo { |key, val|
					file.write("\t" ++ (key -> val).cs ++ ",\n");
				};
				file.write("]\n");
				file.close;
				"MKtlDesc cache written at %.\n".postf(path);
			} {
				warn("MKtlDesc: could not write cache at %.\n".format(path));
			}
		};
	}

	*loadCache {
		// clear first? maybe better not
		descFolders.do { |folder|
			var loadedList = (folder +/+ cacheName).load;
			//	("// loadedList: \n" + loadedList.cs).postln;
			if (loadedList.isNil) {
				"% : no cache file found.\n".postf(thisMethod);
				^this
			};
			loadedList.keysValuesDo { |filename, idInfo|
				fileToIDDict.put(filename, idInfo);
			};
		};
	}

	*updateCache {
		// check if any files have changed,
		// and if so, make a new cache file.
	}


	// ANALYSIS of loaded descs:
	*descKeysUsed {
		// all keys used in fullDescs
		var allKeys = MKtlDesc.allDescs.collectAs(_.fullDesc, Array)
		.collect(_.keys(Array));
		var keySet = allKeys.flat.asSet.collectAs({ |key|
			[key, allKeys.count(_.includes(key)) ];

		}, Array).sort { |a, b| a[1] > b[1] };
		"\n\n/*** All keys used in MKtlDesc.allDescs: ***/\n".postln;
		keySet.do { |list, i|
			[i, list].postcs;
		};
		"\n/*** end MKtlDesc.allDescs - keys. ***/\n".postln;
		^keySet
	}

	*deviceTypesUsed {
		var types = Set.new;
		MKtlDesc.allDescs.do {|d|
			var devtype = d.fullDesc.deviceType;
			if (devtype.notNil) { types = types.add(devtype) };
		};
		^types.asArray.sort;
	}

	*elementTypesUsed {
		var allUsed = Set[];
		MKtlDesc.allDescs.do { |desc|
			allUsed = allUsed.union(desc.elementTypesUsed)
		};
		^allUsed.asArray.sort
	}

	elementTypesUsed {
		var used = Set[];
		var getFunc = { |elem|
			if (elem.isKindOf(Dictionary)) {
				if(elem[\elementType].notNil) {
					used = used.add(elem[\elementType]); };
				elem.do (getFunc);
			};
			if (elem.isKindOf(Array)) {
				elem.do (getFunc);
			};
		};
		this.elementsDesc.do { |elDesc|
			getFunc.(elDesc);
		};
		^used.asArray.sort
	}


	// integrity checks for dicts at all levels:

	// according to current definition,
	// \idInfo, \protocol, \elementsDesc are required;

	*isValidDescDict { |dict|
		var ok = dict.isKindOf(Dictionary)
		and:  ({ dict[\parentDesc].notNil
			or: { dict[\idInfo].notNil
				and: { dict[\protocol].notNil
					and: { dict[\elementsDesc].notNil
						//	and: { this.checkElementsDesc(dict) }
					}
				}
		}});
		if (ok) { ^true };
		// todo: more detailed info here
	//	"% - dict not valid: %\n\n".postf(thisMethod, dict.deviceName);
		^false
	}

	*isValidElemDesc { |dict, protocol|
		var ok = dict.isKindOf(Dictionary)
		and: { dict[\elementType].notNil
			and: { dict[\ioType].notNil
				and: { dict[\spec].notNil }
			}
		};
		if (ok) { ^true };
	//	"% - elemDesc not valid: %\n\n".postf(thisMethod, dict);
	}

	// to be defined and tested
	*isValidMIDIDesc { |dict|
		^dict[\midiMsgType].notNil
	}
	*isValidHIDDesc { |dict|
		^(dict[\usage].notNil
			and: { dict[\usagePage].notNil })
		or: { dict[\hidElementID].notNil }
	}

	*isValidOSCDesc { |dict|
		true
	}

	// plug shared properties in as parents
	*sharePropsToElements { |dict, toShare|
		var shared, elements, subProps;
		if (dict.isKindOf(Dictionary).not) {
		//	"cant share in %\n".postf(dict);
			^this
		};

		shared = dict[\shared] ? ();
		elements = dict[\elements];
		if (toShare.notNil) {
		//	"shared: % parent: %\n\n".postf(shared, toShare);
			shared.parent = toShare;
		};
		elements.do { |elemDict|
			if (elemDict[\elements].notNil) {
				this.sharePropsToElements(elemDict, shared);
			} {
			//	"elem: % shared: %\n\n".postf(elemDict, shared);
				elemDict.parent = shared
			};
		};
	}


	// creation methods

	*fromFileName { |filename, folderIndex, multi = false|
		var paths = this.findFile(filename, folderIndex, false);
		if (paths.isEmpty) {
			warn("MktlDesc: could not find desc with filename %.\n"
				.format(filename));
			^nil;
		};
		if (multi.not) {
			if (paths.size > 1) {
				warn("MktlDesc: found multiple files!\n"
					"loading only first of %: %.\n"
					.format(paths.size, paths[0].basename));
				^this.fromPath(paths[0]);
			};
		};

		^paths.collect(this.fromPath(_)).unbubble;
	}

	*fromPath { |path|
		var desc = path.load;
		if (desc.isNil) {
			warn("MktlDesc: could not load desc from path %.\n"
				.format(path));
			^nil;
		};

		if (this.isValidDescDict(desc).not) {
			warn("desc not valid - %"
				.format(path.basename.splitext[0]));
			^nil
		};
		// got here, should work now
		desc.path = path;
		desc.filename = path.basename.drop(descExt.size.neg);
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
		var foundObj = this.at(name.asSymbol);
		if (foundObj.notNil) {
			^foundObj;
		};

		if (name.notNil) {
			^this.fromFileName(name);
		};
		// for making it from dict
		// post a warning here?
		^super.new;
	}

	// initialisation/preparation

	fullDesc_ { |inDesc|
		var missing;
		if (this.class.isValidDescDict(inDesc).not) {
			warn("MKtlDesc: dict is not a valid desc,"
				" so cannot make elements.");
			^this
		};
		// "fullDesc: inDesc is ok, filename: %\npath: %\n"
		// .postf(inDesc.filename, inDesc.path);

		fullDesc = inDesc;
		path = path ?? { fullDesc[\path]; };

		this.findParent;

		// make elements in both forms
		this.inferName;
		elementsDict = ();
		this.makeElemKeys(this.elementsDesc, []);
		MKtlDesc.sharePropsToElements(this.elementsDesc);

		if (this.protocol == \midi) {
			this.getMidiMsgTypes;
			missing = fullDesc[\elementsWithMissingType];
			if (missing.size > 0) {
				("" + this + "is missing 'midiMsgType' entry for %.")
				.format(missing).warn;
			};
		};

		this.resolveDescEntriesForPlatform;
	}

	dictAt { |key| ^elementsDict[key] }

	// not expanding yet - not sure if needed
	elAt { |... args|
		var res = this.elementsDesc;
		args.do { |key|
			case { key.isNumber } {
				res = res[\elements][key]
			} {
				res = res.elements.detect { |el| el[\key] == key }
			};
			if (res.isNil) {
				^res
			};
		};
		^res
	}

	findParent {
		var parentName = fullDesc[\parentDesc];
		var parentPath, parentDesc;

		if (parentName.isNil) { ^this };

		// "parent: % \n".postf(parentName);
		parentPath = MKtlDesc.findFile(parentName, fileExt: parentExt);

		switch(parentPath.size,
			0, { "no parent found.".postln; },
			1, {
				// "parent found ... ".postln;
				parentDesc = parentPath[0].loadPaths[0];
				// "loaded ...".postln;
				if (parentDesc.isKindOf(Dictionary)) {
					this.fullDesc.parent_(parentDesc);
					// "and adopted.".postln;
				};

			},
			{ "multiple parents found ???".postln; }
		);
	}

	makeElemKeys { |dict, deepKeys|
		var key = dict[\key];
		var elemKey;
		deepKeys = (deepKeys.copy ?? {[]}).add(key);
		if (dict.elements.isNil) {
			elemKey = deepKeys.reject(_.isNil).join($_).asSymbol;
			dict.put(\elemKey, elemKey);
			elementsDict.put(elemKey, dict);
		} {
			dict.elements.do { |elem, i|
				elem[\key] ?? { elem[\key] = (i+1).asSymbol };
				this.makeElemKeys(elem, deepKeys);
			};
		}
	}

	inferName { |inname, force = false|

		if (name.notNil and: force.not) {
			^this
		};

		name = inname ?? {
			fullDesc[\descName] ?
			fullDesc[\name] ?
			fullDesc[\filename];
		};
		if (name.isNil and: { path.notNil }) {
			name = path.basename.drop(descExt.size.neg);
		};

		if (name.isNil) {
			warn("MKtlDesc: could not create valid name, so desc remains\n"
				"unnamed, and will not show up in MKtlDesc.allDescs.");
		} {
			name = name.asSymbol;
			allDescs.put(name, this);
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

	elementsDesc { ^fullDesc[\elementsDesc] }
	elementsDesc_ { |type| ^fullDesc[\elementsDesc] = type }

	specialMessage {|name|
		if ( fullDesc[\specialMessages].notNil) {
			^fullDesc[\specialMessages][name]
		}
	}
	specialMessageNames {
		if ( fullDesc[\specialMessages].notNil) {
			^fullDesc[\specialMessages].keys
		}
	}

	deviceFilename {
		^path !? { path.basename.drop(descExt.size.neg) }
	}

	postInfo { |postElements = false|
		var elements = this.elementsDesc.elements;
		("---\n//" + this + $:) .postln;
		"deviceFilename: %\n".postf(this.deviceFilename);
		"protocol: %\n".postf(this.protocol);
		"idInfo: %\n".postf(this.idInfo);
		"desc keys: %\n".postf(this.elementsDesc.keys);
		"elements keys: %\n".postf(elements !? { elements.collect(_.key) });

		if (postElements) { this.postElements };
	}

	// FIXME
	postElements {
		this.elementsDesc.traverseDo({ |el, deepKeys|
			deepKeys.size.do { $\t.post };
			el.name.post; deepKeys.postcs;
		}, (_.isKindOf(Dictionary)),
		{ |node, deepKeys|
			deepKeys.size.do { $\t.post };
			node.postln
		});
	}

	writeFile { |path|
		"! more than nice to have ! - not done yet.".postln;
	}

	storeArgs { ^[name] }
	printOn { |stream|
		stream << this.class.name << ".at(%)".format(name.cs);
	}

	getMidiMsgTypes {
		var msgTypesUsed = Set.new;
		var type, missing = List[];

		this.elementsDesc.traverseDo ({ |elem, deepKeys|
			var msgType;
			if (deepKeys.last != \shared) {
				MKtlDesc.fillMidiDefaults(elem);
				msgType = elem[\midiMsgType];

				if (msgType.notNil) {
					msgTypesUsed.add(msgType.unbubble);
				} {
					//	"missing: ".post;
					missing.add(elem.elemKey);
				};
				// [elemKey, elem].postln;
			};
		}, MKtlDesc.isElemFunc);


		// treat noteOnOff as noteOn / noteOff
		if (msgTypesUsed.includes(\noteOnOff)) {
			msgTypesUsed.add(\noteOn);
			msgTypesUsed.add(\noteOff);
			msgTypesUsed.remove(\noteOnOff);
		};

		fullDesc.put(\msgTypesUsed, msgTypesUsed);

		if (missing.notEmpty) {
			fullDesc.put(\elementsWithMissingType, missing);
		};
	}

	*fillMidiDefaults { |elemDict|
		// if type = button and no spec, and midiBut.asSpec;
		// if slider and no spec, assume cc message and midiCC.asSpec;
	}


	// some keys may be platform-dependent, e.g.
	// (meaning: (osx: 23, linux: 42, win: 4711));
	// these are resolved for the platform used,
	// e.g. for linux: (meaning: 42)
	*resolveForPlatform { |dict|
		var platForms = [\osx, \linux, \win];
		var myPlatform = thisProcess.platform.name;

		var entry, key, foundval;

		if (dict.isKindOf(Association)) {
			entry = dict.value; key = dict.key;
			if (entry.isKindOf(Dictionary) and:
				{ entry.keys.sect(platForms).notEmpty }) {
				foundval = entry[myPlatform];
				// "MKtlDesc:resolveForPlatform - replacing: ".post;
				^key -> foundval;
		} };

		if (dict.isKindOf(Dictionary)) {
			dict.keysValuesDo { |dictkey, entry|
				var foundPlatformDep = false, foundval;
				if (entry.isKindOf(Dictionary)) {
					foundPlatformDep = entry.keys.sect(platForms).notEmpty;
				};
				if (foundPlatformDep) {
					foundval = entry[myPlatform];
					// "MKtlDesc:resolveForPlatform - replacing: ".post;
					dict.put(dictkey, foundval);
				};
			}
			^dict
		}
		// cant change it
		^dict
	}

	resolveDescEntriesForPlatform {
		if (fullDesc.isNil) { ^this };
		this.class.resolveForPlatform(fullDesc);
		this.elementsDesc.keysValuesDo { |key, elemDesc|
			MKtlDesc.resolveForPlatform(elemDesc);
		};
		this.class.resolveForPlatform(this.elementsDesc);
	}
}
