/*
 * Copyright (c) 2010-2017 Evolveum
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

import com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.prism.PrismObjectWrapper;
import com.evolveum.midpoint.gui.impl.prism.ContainerWrapperImpl;
import com.evolveum.midpoint.gui.impl.prism.ObjectWrapperOld;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerWrapperImpl;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

import org.apache.wicket.model.IModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Model that returns property real values. This implementation works on ObjectWrapper models (not PrismObject).
 *
 *
 * @author katkav
 */
@Deprecated
public class ContainerWrapperListFromObjectWrapperModel<C extends Containerable,O extends ObjectType> extends AbstractWrapperModel<List<PrismContainerWrapper<C>> ,O> {

   private static final long serialVersionUID = 1L;

	private static final Trace LOGGER = TraceManager.getTrace(ContainerWrapperListFromObjectWrapperModel.class);

    private List<ItemPath> paths;

   
    public ContainerWrapperListFromObjectWrapperModel(IModel<PrismObjectWrapper<O>> model, List<ItemPath> paths) {
    	super(model);
//        Validate.notNull(paths, "Item path must not be null.");
        this.paths = paths;
    }


    @Override
    public void detach() {
    }

	@Override
	public List<PrismContainerWrapper<C>> getObject() {
		List<PrismContainerWrapper<C>> wrappers = new ArrayList<>();
//		if (paths == null) {
//			return (List) getWrapper().getContainers();
//		}
//		for (ItemPath path : paths) {
//			PrismContainerWrapper<C> containerWrapper;
//			try {
//				containerWrapper = getWrapper().findContainer(path);
//			} catch (SchemaException e) {
//				return null;
//			}
//			if (containerWrapper != null) {
//				containerWrapper.setShowEmpty(true, false);
//				wrappers.add(containerWrapper);
//			}
//		}
		return wrappers;
	}

	@Override
	public void setObject(List<PrismContainerWrapper<C>> arg0) {
		throw new UnsupportedOperationException("ContainerWrapperFromObjectWrapperModel.setObject called");

	}

}
