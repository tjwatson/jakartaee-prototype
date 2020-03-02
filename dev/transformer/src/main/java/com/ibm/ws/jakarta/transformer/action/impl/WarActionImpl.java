package com.ibm.ws.jakarta.transformer.action.impl;

import com.ibm.ws.jakarta.transformer.action.ActionType;
import com.ibm.ws.jakarta.transformer.action.WarAction;

public class WarActionImpl extends ContainerActionImpl implements WarAction {
	public WarActionImpl(
		LoggerImpl logger,
		InputBufferImpl buffer,
		SelectionRuleImpl selectionRule,
		SignatureRuleImpl signatureRule) {

		super(logger, buffer, selectionRule, signatureRule);
	}

	//

	public String getName() {
		return "WAR Action";
	}

	@Override
	public ActionType getActionType() {
		return ActionType.WAR;
	}

	//

	@Override
	protected WarChangesImpl newChanges() {
		return new WarChangesImpl();
	}

	@Override
	public WarChangesImpl getChanges() {
		return (WarChangesImpl) super.getChanges();
	}

	//

	@Override
	public String getAcceptExtension() {
		return ".war";
	}
}
