package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.PrintStream;

import com.ibm.ws.jakarta.transformer.action.XMLChanges;

public class XMLChangesImpl extends ChangesImpl implements XMLChanges {
	public XMLChangesImpl() {
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

	//

	@Override
	public void displayChanges(PrintStream printStream, String inputPath, String outputPath) {
		// EMPTY
	}
}
