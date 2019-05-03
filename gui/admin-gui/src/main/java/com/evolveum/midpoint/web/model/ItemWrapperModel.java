/*
 * Copyright (c) 2010-2019 Evolveum
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
package com.evolveum.midpoint.web.model;

import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.prism.ItemWrapper;
import com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

/**
 * @author katka
 *
 */
public abstract class ItemWrapperModel<C extends Containerable, IW extends ItemWrapper> implements IModel<IW> {

	private static final transient Trace LOGGER  = TraceManager.getTrace(ItemWrapperModel.class);
	
	
	private IModel<?> parent;
	private ItemPath path;
	private boolean fromContainerValue;
	
	
	ItemWrapperModel(IModel<?> parent, ItemPath path, boolean fromContainerValue) {
		this.parent = parent;
		this.path = path;
		this.fromContainerValue = fromContainerValue;
	}
	
	<W extends ItemWrapper> W getItemWrapper(Class<W> type) {
		try {
			
			if (fromContainerValue) {
				LOGGER.trace("Finding {} with path {} in {}", type.getSimpleName(), path, parent.getObject());
				return ((PrismContainerValueWrapper<C>)parent.getObject()).findItem(path, type);
			}
			
			LOGGER.trace("Finding {} with path {} in {}", type.getSimpleName(), path, parent.getObject());
			return ((PrismContainerWrapper<C>)parent.getObject()).findItem(path, type);
		} catch (SchemaException e) {
			LOGGER.error("Cannot get {} with path {} from parent {}\nReason: {}", type, path, parent, e.getMessage(), e);
			return null;
		}
	}
	
}