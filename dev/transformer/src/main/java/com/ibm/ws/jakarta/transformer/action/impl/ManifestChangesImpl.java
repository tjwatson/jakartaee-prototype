package com.ibm.ws.jakarta.transformer.action.impl;

import com.ibm.ws.jakarta.transformer.action.ManifestChanges;

public class ManifestChangesImpl extends ChangesImpl implements ManifestChanges {
	public ManifestChangesImpl() {
		super();
	}

	@Override
	public boolean hasChanges() {
		return ( super.hasChanges() || (getReplacements() > 0) );
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
}
