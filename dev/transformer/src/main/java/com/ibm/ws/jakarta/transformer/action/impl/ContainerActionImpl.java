package com.ibm.ws.jakarta.transformer.action.impl;

import java.io.File;
import java.util.List;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.action.Action;
import com.ibm.ws.jakarta.transformer.action.ActionType;
import com.ibm.ws.jakarta.transformer.action.ContainerAction;
import com.ibm.ws.jakarta.transformer.util.ByteData;

public abstract class ContainerActionImpl extends ActionImpl implements ContainerAction {

	public <A extends ActionImpl> A addUsing(ActionInit<A> init) {
		A action = createUsing(init);
		addAction(action);
		return action;
	}

	public ContainerActionImpl(
		LoggerImpl logger,
		InputBufferImpl buffer,
		SelectionRuleImpl selectionRule,
		SignatureRuleImpl signatureRule) {

		super(logger, buffer, selectionRule, signatureRule);

		this.compositeAction = createUsing( CompositeActionImpl::new );
	}

	//

	private final CompositeActionImpl compositeAction;

	@Override
	public CompositeActionImpl getAction() {
		return compositeAction;
	}

	public void addAction(ActionImpl action) {
		getAction().addAction(action);
	}

	@Override
	public List<ActionImpl> getActions() {
		return getAction().getActions();
	}

	@Override
	public String getAcceptExtension() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ActionImpl acceptAction(String resourceName) {
		return acceptAction(resourceName, null);
	}

	@Override
	public ActionImpl acceptAction(String resourceName, File resourceFile) {
		return getAction().acceptAction(resourceName, resourceFile);
	}

	//

	@Override
	public abstract String getName();

	@Override
	public abstract ActionType getActionType();

	//

	@Override
	protected abstract ContainerChangesImpl newChanges();

	@Override
	public ContainerChangesImpl getChanges() {
		return (ContainerChangesImpl) super.getChanges();
	}

	//

	protected void recordUnaccepted(String resourceName) {
		verbose( "Resource [ %s ]: Not accepted\n", resourceName );

		getChanges().record();
	}

	protected void recordUnselected(Action action, boolean hasChanges, String resourceName) {
		verbose(
			"Resource [ %s ] Action [ %s ]: Accepted but not selected\n",
			resourceName, action.getName() );

		getChanges().record(action, hasChanges);
	}

	protected void recordTransform(Action action, String resourceName) {
		verbose(
			"Resource [ %s ] Action [ %s ]: Changes [ %s ]\n",
			resourceName, action.getName(), action.hasChanges() );

		getChanges().record(action);
	}

	// Byte base container conversion is not supported.

	@Override
	public ByteData apply(String inputName, byte[] inputBytes, int inputLength)
		throws JakartaTransformException {
		throw new UnsupportedOperationException();
	}
}