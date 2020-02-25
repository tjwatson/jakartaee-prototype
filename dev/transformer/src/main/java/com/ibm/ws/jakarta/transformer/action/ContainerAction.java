package com.ibm.ws.jakarta.transformer.action;

import java.io.File;
import java.util.List;

public interface ContainerAction extends Action {
	@Override
	ContainerChanges getChanges();

	CompositeAction getAction();
	List<? extends Action> getActions();
	
	Action acceptAction(String resourceName);	
	Action acceptAction(String resourceName, File resourceFile);
}
