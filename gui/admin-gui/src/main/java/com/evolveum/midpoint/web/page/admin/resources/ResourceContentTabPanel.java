/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.page.admin.resources;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.common.refinery.RefinedResourceSchemaImpl;
import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.web.application.PanelType;
import com.evolveum.midpoint.web.component.form.MidpointForm;
import com.evolveum.midpoint.web.session.SessionStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ContainerPanelConfigurationType;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.gui.api.component.autocomplete.AutoCompleteQNamePanel;
import com.evolveum.midpoint.gui.api.component.autocomplete.AutoCompleteTextPanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.processor.ObjectClassComplexTypeDefinition;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.admin.resources.content.dto.ResourceContentSearchDto;
import com.evolveum.midpoint.web.session.ResourceContentStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;

import org.opensaml.xacml.policy.ResourcesType;

/**
 * @author katkav
 * @author semancik
 */
public class ResourceContentTabPanel extends BasePanel<PrismObject<ResourceType>> {
    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(ResourceContentTabPanel.class);

    enum Operation {
        REMOVE, MODIFY;
    }

    private static final String DOT_CLASS = ResourceContentTabPanel.class.getName() + ".";

    private static final String ID_INTENT = "intent";
    private static final String ID_REAL_OBJECT_CLASS = "realObjectClass";
    private static final String ID_OBJECT_CLASS = "objectClass";
    private static final String ID_MAIN_FORM = "mainForm";

    private static final String ID_REPO_SEARCH = "repositorySearch";
    private static final String ID_RESOURCE_SEARCH = "resourceSearch";

    private static final String ID_TABLE = "table";

    private ShadowKindType kind;

    private boolean useObjectClass;
    private boolean isRepoSearch = true;

    private IModel<ResourceContentSearchDto> resourceContentSearch;

    public ResourceContentTabPanel(String id, final ShadowKindType kind,
            final IModel<PrismObject<ResourceType>> model) {
        super(id, model);

        this.kind = kind;
        this.resourceContentSearch = createContentSearchModel(kind);
        //TODO config
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    private IModel<ResourceContentSearchDto> createContentSearchModel(final ShadowKindType kind) {
        return new LoadableModel<ResourceContentSearchDto>(true) {

            private static final long serialVersionUID = 1L;

            @Override
            protected ResourceContentSearchDto load() {
                isRepoSearch  = !getContentStorage(kind, SessionStorage.KEY_RESOURCE_PAGE_REPOSITORY_CONTENT).getResourceSearch();
                return getContentStorage(kind, isRepoSearch ? SessionStorage.KEY_RESOURCE_PAGE_REPOSITORY_CONTENT :
                        SessionStorage.KEY_RESOURCE_PAGE_RESOURCE_CONTENT).getContentSearch();

            }

        };

    }

    private void updateResourceContentSearch() {
        ResourceContentSearchDto searchDto = resourceContentSearch.getObject();
        getContentStorage(kind, isRepoSearch ? SessionStorage.KEY_RESOURCE_PAGE_REPOSITORY_CONTENT :
                SessionStorage.KEY_RESOURCE_PAGE_RESOURCE_CONTENT).setContentSearch(searchDto);
    }

    private ResourceContentStorage getContentStorage(ShadowKindType kind, String searchMode) {
        return getPageBase().getSessionStorage().getResourceContentStorage(kind, searchMode);
    }

    private void initLayout() {
        setOutputMarkupId(true);

        final Form mainForm = new MidpointForm(ID_MAIN_FORM);
        mainForm.setOutputMarkupId(true);
        mainForm.addOrReplace(initTable(getModel()));
        add(mainForm);

        AutoCompleteTextPanel<String> intent = new AutoCompleteTextPanel<String>(ID_INTENT,
            new PropertyModel<>(resourceContentSearch, "intent"), String.class, false, null) {
            private static final long serialVersionUID = 1L;

            @Override
            public Iterator<String> getIterator(String input) {
                RefinedResourceSchema refinedSchema = null;
                try {
                    refinedSchema = RefinedResourceSchemaImpl.getRefinedSchema(getModelObject(),
                            getPageBase().getPrismContext());

                } catch (SchemaException e) {
                    return new ArrayList<String>().iterator();
                }
                return RefinedResourceSchemaImpl.getIntentsForKind(refinedSchema, getKind()).iterator();

            }

        };
        intent.getBaseFormComponent().add(new OnChangeAjaxBehavior() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                target.add(get(ID_REAL_OBJECT_CLASS));
                updateResourceContentSearch();
                mainForm.addOrReplace(initTable(getModel()));
                target.add(mainForm);

            }
        });
        intent.setOutputMarkupId(true);
        intent.add(new VisibleEnableBehaviour() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible() {
                return !isUseObjectClass();
            }
        });
        add(intent);

        Label realObjectClassLabel = new Label(ID_REAL_OBJECT_CLASS, new IModel<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public String getObject() {
                RefinedObjectClassDefinition ocDef;
                try {
                    RefinedResourceSchema refinedSchema = RefinedResourceSchemaImpl
                            .getRefinedSchema(getModelObject(), getPageBase().getPrismContext());
                    if (refinedSchema == null) {
                        return "NO SCHEMA DEFINED";
                    }
                    ocDef = refinedSchema.getRefinedDefinition(getKind(), getIntent());
                    if (ocDef != null) {
                        return ocDef.getObjectClassDefinition().getTypeName().getLocalPart();
                    }
                } catch (SchemaException e) {
                }

                return "NOT FOUND";
            }
        });
        realObjectClassLabel.setOutputMarkupId(true);
        add(realObjectClassLabel);

        AutoCompleteQNamePanel objectClassPanel = new AutoCompleteQNamePanel(ID_OBJECT_CLASS,
            new PropertyModel<>(resourceContentSearch, "objectClass")) {
            private static final long serialVersionUID = 1L;

            @Override
            public Collection<QName> loadChoices() {
                return createObjectClassChoices(getModel());
            }

            @Override
            protected void onChange(AjaxRequestTarget target) {
                LOGGER.trace("Object class panel update: {}", isUseObjectClass());
                updateResourceContentSearch();
                mainForm.addOrReplace(initTable(getModel()));
                target.add(mainForm);
            }

        };

        objectClassPanel.add(new VisibleEnableBehaviour() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible() {
                return isUseObjectClass();
            }
        });
        add(objectClassPanel);

        AjaxLink<Boolean> repoSearch = new AjaxLink<Boolean>(ID_REPO_SEARCH,
            new PropertyModel<>(resourceContentSearch, "resourceSearch")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                isRepoSearch = true;
                getContentStorage(kind, SessionStorage.KEY_RESOURCE_PAGE_REPOSITORY_CONTENT).setResourceSearch(Boolean.FALSE);
                getContentStorage(kind, SessionStorage.KEY_RESOURCE_PAGE_RESOURCE_CONTENT).setResourceSearch(Boolean.FALSE);

                resourceContentSearch.getObject().setResourceSearch(Boolean.FALSE);
                updateResourceContentSearch();
                mainForm.addOrReplace(initRepoContent(ResourceContentTabPanel.this.getModel()));
                target.add(getParent().addOrReplace(mainForm));
                target.add(this);
                target.add(getParent().get(ID_RESOURCE_SEARCH)
                        .add(AttributeModifier.replace("class", "btn btn-sm btn-default")));
            }

            @Override
            protected void onBeforeRender() {
                super.onBeforeRender();
                if (!getModelObject().booleanValue())
                    add(AttributeModifier.replace("class", "btn btn-sm btn-default active"));
            }
        };
        add(repoSearch);

        AjaxLink<Boolean> resourceSearch = new AjaxLink<Boolean>(ID_RESOURCE_SEARCH,
            new PropertyModel<>(resourceContentSearch, "resourceSearch")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                isRepoSearch = false;
                getContentStorage(kind, SessionStorage.KEY_RESOURCE_PAGE_REPOSITORY_CONTENT).setResourceSearch(Boolean.TRUE);
                getContentStorage(kind, SessionStorage.KEY_RESOURCE_PAGE_RESOURCE_CONTENT).setResourceSearch(Boolean.TRUE);
                updateResourceContentSearch();
                resourceContentSearch.getObject().setResourceSearch(Boolean.TRUE);
                mainForm.addOrReplace(initResourceContent(ResourceContentTabPanel.this.getModel()));
                target.add(getParent().addOrReplace(mainForm));
                target.add(this.add(AttributeModifier.append("class", " active")));
                target.add(getParent().get(ID_REPO_SEARCH)
                        .add(AttributeModifier.replace("class", "btn btn-sm btn-default")));
            }

            @Override
            protected void onBeforeRender() {
                super.onBeforeRender();
                getModelObject().booleanValue();
                if (getModelObject().booleanValue())
                    add(AttributeModifier.replace("class", "btn btn-sm btn-default active"));
            }
        };
        add(resourceSearch);

    }

    private List<QName> createObjectClassChoices(IModel<PrismObject<ResourceType>> model) {
        RefinedResourceSchema refinedSchema;
        try {
            refinedSchema = RefinedResourceSchemaImpl.getRefinedSchema(model.getObject(),
                    getPageBase().getPrismContext());
        } catch (SchemaException e) {
            warn("Could not determine defined obejct classes for resource");
            return new ArrayList<>();
        }
        Collection<ObjectClassComplexTypeDefinition> defs = refinedSchema.getObjectClassDefinitions();
        List<QName> objectClasses = new ArrayList<>(defs.size());
        for (ObjectClassComplexTypeDefinition def : defs) {
            objectClasses.add(def.getTypeName());
        }
        return objectClasses;
    }

    private ResourceContentPanel initTable(IModel<PrismObject<ResourceType>> model) {
        if (isResourceSearch()) {
            return initResourceContent(getModel());
        } else {
            return initRepoContent(getModel());
        }
    }

    private ResourceContentResourcePanel initResourceContent(IModel<PrismObject<ResourceType>> model) {
        String searchMode = isRepoSearch ? SessionStorage.KEY_RESOURCE_PAGE_REPOSITORY_CONTENT :
                SessionStorage.KEY_RESOURCE_PAGE_RESOURCE_CONTENT;
        ResourceContentResourcePanel resourceContent = new ResourceContentResourcePanel(ID_TABLE, loadResourceModel(),
                getObjectClass(), getKind(), getIntent(), searchMode, getPageBase());
        resourceContent.setOutputMarkupId(true);
        return resourceContent;

    }

    private ResourceContentRepositoryPanel initRepoContent(IModel<PrismObject<ResourceType>> model) {
        String searchMode = isRepoSearch ? SessionStorage.KEY_RESOURCE_PAGE_REPOSITORY_CONTENT :
                SessionStorage.KEY_RESOURCE_PAGE_RESOURCE_CONTENT;
        ResourceContentRepositoryPanel repositoryContent = new ResourceContentRepositoryPanel(ID_TABLE, loadResourceModel(),
                getObjectClass(), getKind(), getIntent(), searchMode, getPageBase());
        repositoryContent.setOutputMarkupId(true);
        return repositoryContent;
    }

    private LoadableModel<PrismObject<ResourceType>> loadResourceModel() {
        return new LoadableModel<>(false) {

            @Override
            protected PrismObject<ResourceType> load() {
                return getModelObject();
            }
        };
    }

    private ShadowKindType getKind() {
        return resourceContentSearch.getObject().getKind();
    }

    private String getIntent() {
        return resourceContentSearch.getObject().getIntent();
    }

    private QName getObjectClass() {
        return resourceContentSearch.getObject().getObjectClass();
    }

    private boolean isResourceSearch() {
        Boolean isResourceSearch = resourceContentSearch.getObject().isResourceSearch();
        if (isResourceSearch == null) {
            return false;
        }
        return resourceContentSearch.getObject().isResourceSearch();
    }

    private boolean isUseObjectClass() {
        return resourceContentSearch.getObject().isUseObjectClass();
    }

}
