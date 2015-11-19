// only useful with a device,
// so sort of abstract superclass.

MKtlDevice {

	// ( 'midi': List['name1',... ], 'hid': List['name1',... ], ... )

	classvar <>verbose = true;

	// classvar <allAvailable;
	classvar <allProtocols;
	classvar <subClassDict;

	classvar <deviceTypes;

	// lookup name, full device name, the mktl it was made for
	var <name, <deviceName, <>mktl;

	var <traceRunning = false;

	trace { |bool = true|
		traceRunning = bool;
	}

	*initClass {
		// allAvailable = ();

		if ( Main.versionAtLeast( 3, 7 ) ) {
			// this order seems to work better on osx
			// order \midi, \hid ... crashes interpreter when server is on
			// see https://github.com/supercollider/supercollider/issues/1640
			allProtocols = [\hid,\midi,\osc];
		} {
			allProtocols = [\midi,\osc];
		};
		deviceTypes = List[
			'controller', 'djController', 'drumPad', 'drumpad', 'fader',
			'faderbox', 'filterbank', 'gamepad', 'hidKeyboard','joystick',
			'launchpad', 'manta', 'midiKeyboard', 'mixer', 'mouse',
			'multiController', 'phoneApp', 'push', 'ribbon' ];

		subClassDict = ();
		this.allSubclasses.do { |cl| subClassDict.put(cl.protocol, cl) };
	}

	*subFor { |protocol|
		protocol = protocol ? allProtocols;
		^protocol.asArray.collect { |proto| subClassDict[proto] }.unbubble;
	}

	*find { |protocols, post = true|
		this.subFor(protocols).do (_.find(false));
		if (post) {
			this.subFor(protocols).do(_.postPossible);
		};
	}

	*descFileStrFor { |nameKey, filenames, multiIndex|

		var str = filenames.size.switch(
			0, 	{ "\t\t// no matching desc files found!\n"; },
			1, 	{ "\t\t// create from desc file:\n"; },
			{ 	"\t\t// multiple desc files found!\n"
				"\t\t//choose one for the MKtl:\n";
		});

		filenames.do { |filename|
		str = str ++ "MKtl(%, %);\n".format(
			nameKey.cs,
			filename.cs,
				if (multiIndex.notNil, "," + multiIndex, ""
				)
			);
		};
		^str ++ "\n";
	}


	*initHardwareDevices { |force = false, protocols|
		this.subFor(protocols).do { |cl|
			cl.initDevices( force );
		};
	}

	*open { |name, parentMKtl, multiIndex|
		var lookupName, lookupInfo, protocol, idInfo;
		var desc, subClass, newDevice;
		var deviceCandidates;

		if (parentMKtl.isNil) {
			"%: parentMktl.isNil - should not happen!\n".postf(thisMethod);
			^nil
		};

		// try to find device by lokupName/info first:
		lookupName = parentMKtl.lookupName;
		lookupInfo = parentMKtl.lookupInfo ?? { MKtlLookup.all[lookupName] };
		lookupName = lookupName ?? {
			if (lookupInfo.notNil) { lookupInfo.lookupName }
		};

		// if we know the device lookupName already,
		// and it is a single name only, we can get it from here:
		if (lookupInfo.notNil) {
		//	[lookupName, lookupInfo].postln;
			subClass = MKtlDevice.subFor(lookupInfo.protocol);
			^subClass.new( lookupName, parentMKtl: parentMKtl, multiIndex: multiIndex);
		};

		// no luck with lookup info, so try with desc next

		desc = parentMKtl.desc;
		if (desc.isNil) {
			if (verbose) {
				"MKtldevice.open: parentMktl.desc.isNil"
				" - should not happen!".postln;
			};
			^nil
		};

		protocol = desc.protocol;
		idInfo = desc.idInfo;
		deviceCandidates = MKtlLookup.findByIDInfo(idInfo);

		// "number of device candidates: %\n".postf(deviceCandidates.size);
		if (deviceCandidates.size == 0) {
			if (protocol != \osc) {
				if (verbose) {
					inform("%: could not open device -"
						" no device candidates found."
						.format(thisMethod));
				};
				^nil
			};
		};

		if (deviceCandidates.size > 1) {
			if (multiIndex.notNil) {
				lookupInfo = deviceCandidates[multiIndex];
				if (lookupInfo.notNil) {
					lookupName = lookupInfo.lookupName;
				};
			} {
				inform("%: multiple device candidates found,"
					" please disambiguate by providing a multiIndex!"
					"\nThe candidates are:"
				.format(thisMethod));
				deviceCandidates.printcsAll;
				^nil
			};
		} {
			// we have exactly one candidate, so we take it:
			lookupInfo = deviceCandidates[0];
			if (lookupInfo.notNil) {
				lookupName = lookupInfo.lookupName;
			};
		};

		if (lookupName.notNil) {
			lookupInfo.lookupName = lookupName;
			parentMKtl.updateLookupInfo(lookupInfo);
		} {
			lookupName = name;
		};

		// "% gets to end. lookupName: %.\n\n\n".postf(thisMethod, lookupName);
		subClass = MKtlDevice.subFor(desc.protocol);
		^subClass.new(lookupName,
			parentMKtl: parentMKtl,
			multiIndex: multiIndex);

	}

	*basicNew { |name, deviceName, parentMKtl |
		^super.newCopyArgs(name, deviceName, parentMKtl ).init;
	}

	init { } // overwrite in subclasses

	*protocol {
		this.subclassResponsibility(thisMethod)
	}

	cleanupElementsAndCollectives {
		this.subclassResponsibility(thisMethod)
	}

	initElements {
		this.subclassResponsibility(thisMethod)
	}

	initCollectives {
		this.subclassResponsibility(thisMethod)
	}

	closeDevice {
		this.subclassResponsibility(thisMethod)
	}

	// exploration:

	exploring {
		this.subclassResponsibility(thisMethod)
	}

	explore { |bool = true|
		this.subclassResponsibility(thisMethod)
	}

	createDescriptionFile {
		this.subclassResponsibility(thisMethod)
	}

	// initialisation messages

	sendSpecialMessage {
		this.subclassResponsibility(thisMethod)
	}

}