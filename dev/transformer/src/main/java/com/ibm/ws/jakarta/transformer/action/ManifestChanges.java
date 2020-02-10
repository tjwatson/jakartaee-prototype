package com.ibm.ws.jakarta.transformer.action;

public interface ManifestChanges extends Changes {
	int getReplacements();
	void addReplacement();
	void addReplacements(int additions);
}
