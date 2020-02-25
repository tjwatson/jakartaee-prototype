package com.ibm.ws.jakarta.transformer.action;

import java.io.File;
import java.util.List;

public interface CompositeAction extends Action {
	List<? extends Action> getActions();

	Action acceptAction(String resourceName, File resourceFile);
	Action getAcceptedAction();
}
