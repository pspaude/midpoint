/*
 * Copyright (c) 2010-2018 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.gui.impl.factory;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismPropertyPanel;
import com.evolveum.midpoint.gui.impl.prism.PrismValueWrapper;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismReference;
import com.evolveum.midpoint.prism.PrismReferenceDefinition;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.Referencable;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.web.component.prism.ValueStatus;

/**
 * @author katka
 *
 */
@Component
public class PrismReferenceWrapperFactory<R extends Referencable> extends ItemWrapperFactoryImpl<PrismReferenceWrapper<R>, PrismReferenceValue, PrismReference, PrismReferenceValueWrapper<R>>{

	@Override
	public boolean match(ItemDefinition<?> def) {
		return def instanceof PrismReferenceDefinition;
	}

	@PostConstruct
	@Override
	public void register() {
		getRegistry().addToRegistry(this);
	}

	@Override
	public int getOrder() {
		return Integer.MAX_VALUE;
	}

	@Override
	protected PrismReferenceValue createNewValue(PrismReference item) throws SchemaException {
		PrismReferenceValue prv = getPrismContext().itemFactory().createReferenceValue();
		item.add(prv);
		return prv;
	}

	@Override
	protected PrismReferenceWrapper<R> createWrapper(PrismContainerValueWrapper<?> parent, PrismReference item,
			ItemStatus status) {
		PrismReferenceWrapperImpl<R> wrapper = new PrismReferenceWrapperImpl<>(parent, item, status);
		getRegistry().registerWrapperPanel(item.getDefinition().getTypeName(), PrismPropertyPanel.class);
		return wrapper;
	}
	

	@Override
	public PrismReferenceValueWrapper<R> createValueWrapper(PrismReferenceWrapper<R> parent, PrismReferenceValue value, ValueStatus status,
			WrapperContext context) throws SchemaException {
			
		PrismReferenceValueWrapper<R> refValue = new PrismReferenceValueWrapper<>(parent, value, status);
		return refValue;
	}

}
