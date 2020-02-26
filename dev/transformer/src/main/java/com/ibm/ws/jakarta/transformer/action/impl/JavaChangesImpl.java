package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.PrintStream;

import com.ibm.ws.jakarta.transformer.action.JavaChanges;

public class JavaChangesImpl extends ChangesImpl implements JavaChanges {
    public JavaChangesImpl() {
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

	@Override
	public void displayChanges(PrintStream printStream, String inputPath, String outputPath) {
		// EMPTY
	}
}
