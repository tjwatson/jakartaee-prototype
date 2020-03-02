package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.PrintStream;

import com.ibm.ws.jakarta.transformer.action.NullChanges;

public class NullChangesImpl extends ChangesImpl implements NullChanges {
	public NullChangesImpl() {
		super();
	}

	//

	@Override
	public void displayChanges(PrintStream printStream, String inputPath, String outputPath) {
		printStream.printf(
			"Input  [ %s ] as [ %s ]\n", getInputResourceName(), inputPath);
		printStream.printf(
			"Output [ %s ] as [ %s ]\n", getOutputResourceName(), outputPath);
	}
}
