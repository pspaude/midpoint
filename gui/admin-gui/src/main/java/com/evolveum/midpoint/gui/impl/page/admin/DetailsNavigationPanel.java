/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.model.ReadOnlyModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.web.application.AssignmentCounter;
import com.evolveum.midpoint.web.application.Counter;
import com.evolveum.midpoint.web.application.PanelLoader;
import com.evolveum.midpoint.web.application.SimpleCounter;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ContainerPanelConfigurationType;

import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserInterfaceElementVisibilityType;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class DetailsNavigationPanel<O extends ObjectType> extends BasePanel<List<ContainerPanelConfigurationType>> {

    private static final String ID_NAV = "menu";
    private static final String ID_NAV_ITEM = "navItem";
    private static final String ID_NAV_ITEM_ICON = "navItemIcon";
    private static final String ID_SUB_NAVIGATION = "subNavigation";
    private static final String ID_COUNT = "count";

    private LoadableModel<PrismObjectWrapper<O>> objectModel;

    public DetailsNavigationPanel(String id, LoadableModel<PrismObjectWrapper<O>> objectModel, IModel<List<ContainerPanelConfigurationType>> model) {
        super(id, model);
        this.objectModel = objectModel;
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    private void initLayout() {
        ListView<ContainerPanelConfigurationType> listView = new ListView<>(ID_NAV, getModel()) {

            @Override
            protected void populateItem(ListItem<ContainerPanelConfigurationType> item) {
                WebMarkupContainer icon = new WebMarkupContainer(ID_NAV_ITEM_ICON);
                icon.setOutputMarkupId(true);
                icon.add(AttributeAppender.append("class",
                        item.getModelObject().getDisplay() != null ? item.getModelObject().getDisplay().getCssClass() :
                                GuiStyleConstants.CLASS_CIRCLE_FULL));
                item.add(icon);
                AjaxLink<Void> link = new AjaxLink<>(ID_NAV_ITEM) {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        onClickPerformed(item.getModelObject(), target);
                    }
                };
                link.setBody(Model.of(createButtonLabel(item.getModelObject())));
                item.add(link);

                IModel<String> countModel = createCountModel(item.getModel());
                Label label = new Label(ID_COUNT, countModel);
                label.add(new VisibleBehaviour(() -> countModel.getObject() != null));
                item.add(label);

                DetailsNavigationPanel subPanel = new DetailsNavigationPanel(ID_SUB_NAVIGATION, objectModel, Model.ofList(item.getModelObject().getPanel())) {

                    @Override
                    protected void onClickPerformed(ContainerPanelConfigurationType config, AjaxRequestTarget target) {
                        if (config.getPath() == null) {
                            config.setPath(item.getModelObject().getPath());
                        }
                        DetailsNavigationPanel.this.onClickPerformed(config, target);
                    }
                };
                item.add(subPanel);
                item.add(new VisibleBehaviour(() -> isMenuItemVisible(item.getModelObject())));

//                item.add(new Label(ID_NAV_ITEM, item.getModel()));
            }
        };
        listView.setOutputMarkupId(true);
        add(listView);
    }

    private IModel<String> createCountModel(IModel<ContainerPanelConfigurationType> panelModel) {
        return new ReadOnlyModel<>( () -> {
            ContainerPanelConfigurationType config = panelModel.getObject();
            String panelIdentifier = config.getPanelType();
            Class<?> panelClass = PanelLoader.findPanel(panelIdentifier);
            Counter counter = panelClass.getAnnotation(Counter.class);
            if (counter == null || counter.provider().equals(SimpleCounter.class)) {
                return null;
            }

            Class<?> counterProvider = counter.provider();
            try {
                Constructor<?> constructor = counterProvider.getConstructor(IModel.class);
                AssignmentCounter assoginmentCounter = (AssignmentCounter) constructor.newInstance(objectModel);
                int count = assoginmentCounter.count();
                if (count == 0) {
                    return null;
                }
                return String.valueOf(count);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    private boolean isMenuItemVisible(ContainerPanelConfigurationType config) {
        if (config == null) {
            return true;
        }

        UserInterfaceElementVisibilityType visibility = config.getVisibility();
        if (visibility == null) {
            return true;
        }

        if (UserInterfaceElementVisibilityType.HIDDEN == visibility) {
            return false;
        }

        return true;
    }

    protected void onClickPerformed(ContainerPanelConfigurationType config, AjaxRequestTarget target) {

    }

    private String createButtonLabel(ContainerPanelConfigurationType config) {
        if (config.getDisplay() == null) {
            return "N/A";
        }

        if (config.getDisplay().getLabel() == null) {
            return "N/A";
        }

        return config.getDisplay().getLabel().getOrig();
    }

}
