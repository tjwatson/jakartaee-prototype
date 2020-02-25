package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.PrintStream;

import com.ibm.ws.jakarta.transformer.action.ServiceConfigChanges;

public class ServiceConfigChangesImpl extends ChangesImpl implements ServiceConfigChanges {

	public ServiceConfigChangesImpl() {
		super();

		this.clearChanges();
	}

	//

	@Override
	public boolean hasNonResourceNameChanges() {
		return ( changedProviders > 0 );
	}

	@Override
	public void clearChanges() {
		changedProviders = 0;
		unchangedProviders = 0;

		super.clearChanges();
	}

	//

	private int changedProviders;
	private int unchangedProviders;

	@Override
	public void addChangedProvider() {
		changedProviders++;
	}

	@Override
	public int getChangedProviders() {
		return changedProviders;
	}

	@Override
	public void addUnchangedProvider() {
		unchangedProviders++;
	}

	@Override
	public int getUnchangedProviders() {
		return unchangedProviders;
	}

	//

	@Override
	public void display(PrintStream printStream) {
		printStream.printf( "Resource name [ %s ] [ %s ]\n",
			getInputResourceName(),
			getOutputResourceName() );

		printStream.printf( "Replacements [ %s ]\n",
			getChangedProviders() );
	}
}
