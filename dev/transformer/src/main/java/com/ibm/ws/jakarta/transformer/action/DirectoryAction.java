package com.ibm.ws.jakarta.transformer.action;

public interface DirectoryAction extends Action {
	@Override
	DirectoryChanges getChanges();
}
