package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.PrintStream;

import com.ibm.ws.jakarta.transformer.action.ManifestChanges;

public class ManifestChangesImpl extends ChangesImpl implements ManifestChanges {
	public ManifestChangesImpl() {
		super();
	}

	@Override
	public boolean hasNonResourceNameChanges() {
		return ( getReplacements() > 0 );
	}

	@Override
	public void clearChanges() {
		super.clearChanges();

		replacements = 0;
	}

	//

	private int replacements;

	@Override
	public int getReplacements() {
		return replacements;
	}

	@Override
	public void addReplacement() {
		replacements++;
	}

	@Override
	public void addReplacements(int additions) {
		replacements += additions;
	}

	//

	@Override
	public void displayChanges(PrintStream printStream, String inputPath, String outputPath) {
		printStream.printf(
			"Input  [ %s ] as [ %s ]\n",
       		getInputResourceName(), inputPath );
		
		printStream.printf(
			"Output [ %s ] as [ %s ]\n",
			getOutputResourceName(), outputPath );

		printStream.printf(
			"Replacements  [ %s ]\n", getReplacements() );
	}
}
