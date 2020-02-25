package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.PrintStream;

import com.ibm.ws.jakarta.transformer.action.NullChanges;

public class NullChangesImpl extends ChangesImpl implements NullChanges {
	public NullChangesImpl() {
		super();
	}

	//

	@Override
	public void display(PrintStream printStream) {
		printStream.printf( "Resource name [ %s ] [ %s ]\n",
       		getInputResourceName(),
			getOutputResourceName() );
	}
}
