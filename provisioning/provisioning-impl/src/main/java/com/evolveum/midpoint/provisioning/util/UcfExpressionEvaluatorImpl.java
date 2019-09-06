/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0 
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.provisioning.util;

import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.provisioning.ucf.api.UcfExpressionEvaluator;
import com.evolveum.midpoint.repo.common.expression.Expression;
import com.evolveum.midpoint.repo.common.expression.ExpressionEvaluationContext;
import com.evolveum.midpoint.repo.common.expression.ExpressionFactory;
import com.evolveum.midpoint.repo.common.expression.ExpressionVariables;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.annotation.Experimental;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExpressionType;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@Component
@Experimental
public class UcfExpressionEvaluatorImpl implements UcfExpressionEvaluator {

	@Autowired private ExpressionFactory expressionFactory;
	@Autowired private TaskManager taskManager;

	@NotNull
	@Override
	public <O> List<O> evaluate(ExpressionType expressionBean, VariablesMap variables, QName outputPropertyName,
			String ctxDesc) throws SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException,
			ConfigurationException, ExpressionEvaluationException {
		// TODO consider getting the task instance from the caller
		Task task = taskManager.createTaskInstance(UcfExpressionEvaluatorImpl.class.getName() + ".evaluate");
		OperationResult result = task.getResult();

		Expression<PrismPropertyValue<O>, PrismPropertyDefinition<O>> expression =
				expressionFactory.makePropertyExpression(expressionBean, outputPropertyName, MiscSchemaUtil.getExpressionProfile(), ctxDesc, task, result);
		ExpressionVariables exprVariables = new ExpressionVariables();
		exprVariables.putAll(variables);
		ExpressionEvaluationContext context = new ExpressionEvaluationContext(null, exprVariables, ctxDesc, task, result);
		PrismValueDeltaSetTriple<PrismPropertyValue<O>> exprResultTriple = expression.evaluate(context);
		List<O> list = new ArrayList<>();
		for (PrismPropertyValue<O> pv : exprResultTriple.getZeroSet()) {
			list.add(pv.getRealValue());
		}
		return list;
	}
}
