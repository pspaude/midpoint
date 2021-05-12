package com.evolveum.midpoint.web.page.admin.reports.component;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.component.ContainerableListPanel;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.model.common.util.DefaultColumnUtils;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.Referencable;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.data.ISelectableDataProvider;
import com.evolveum.midpoint.web.component.data.SelectableBeanContainerDataProvider;
import com.evolveum.midpoint.web.component.search.*;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.page.admin.server.dto.OperationResultStatusPresentationProperties;
import com.evolveum.midpoint.web.session.ObjectListStorage;
import com.evolveum.midpoint.web.session.PageStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage;

import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.sort.SortOrder;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.model.IModel;

import javax.xml.namespace.QName;
import java.util.*;

/**
 * @author lskublik
 */

public class ReportObjectsListPanel<C extends Containerable> extends ContainerableListPanel<C, SelectableBean<C>> {

    private static final Trace LOGGER = TraceManager.getTrace(ReportObjectsListPanel.class);

    private IModel<ReportType> report;
    private CompiledObjectCollectionView view;
    private Map<String, Object> variables = new HashMap<>();
    private ObjectListStorage pageStorage;

    public ReportObjectsListPanel(String id, IModel<ReportType> report) {
        super(id, null);
        this.report = report;
    }

    @Override
    protected void onInitialize() {
        initView();
        super.onInitialize();
    }

    @Override
    protected Class<C> getDefaultType() {
        return view.getTargetClass(getPrismContext());
    }

    private void initView() {
        try {
            Task task = getPageBase().createSimpleTask("create compiled view");
            view = getPageBase().getReportManager().createCompiledView(getReport().getObjectCollection(), true, task, task.getResult());
        } catch (Exception e) {
            LOGGER.error("Couldn't create compiled view for report " + getReport());
        }
    }

    private ReportType getReport() {
        return report.getObject();
    }

    @Override
    protected UserProfileStorage.TableId getTableId() {
        return null;
    }

    @Override
    protected C getRowRealValue(SelectableBean<C> rowModelObject) {
        if (rowModelObject == null) {
            return null;
        }
        return rowModelObject.getValue();
    }

    @Override
    protected IColumn<SelectableBean<C>, String> createIconColumn() {
        return null;
    }

    @Override
    protected IColumn<SelectableBean<C>, String> createCheckboxColumn() {
        return null;
    }

    @Override
    protected CompiledObjectCollectionView getObjectCollectionView() {
        return view;
    }

    @Override
    protected boolean isCollectionViewPanel() {
        return true;
    }

    @Override
    protected ISelectableDataProvider<C, SelectableBean<C>> createProvider() {
        SelectableBeanContainerDataProvider<C> provider = new SelectableBeanContainerDataProvider<>(this, getSearchModel(), null, false) {

            @Override
            public List<SelectableBean<C>> createDataObjectWrappers(Class<? extends C> type, ObjectQuery query, Collection<SelectorOptions<GetOperationOptions>> options, Task task, OperationResult result)
                    throws CommunicationException, ObjectNotFoundException, SchemaException, SecurityViolationException, ConfigurationException, ExpressionEvaluationException {
                Collection<SelectorOptions<GetOperationOptions>> defaultOptions = DefaultColumnUtils.createOption(getObjectCollectionView().getTargetClass(getPrismContext()), getSchemaService());
                QName qNameType = WebComponentUtil.containerClassToQName(getPrismContext(), type);
                VariablesMap variables = new VariablesMap();
                if (getSearchModel().getObject() != null) {
                    variables.putAll(getSearchModel().getObject().getFilterVariables(getVariables(), getPageBase()));
                    processReferenceVariables(variables);
                }
                List<C> list = (List<C>) getModelInteractionService().searchObjectsFromCollection(getReport().getObjectCollection().getCollection(), qNameType, defaultOptions, query.getPaging(), variables, task, result);

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Query {} resulted in {} objects", type.getSimpleName(), list.size());
                }

                List<SelectableBean<C>> data = new ArrayList<SelectableBean<C>>();
                for (C object : list) {
                    data.add(createDataObjectWrapper(object));
                }
                return data;
            }

            @Override
            protected Integer countObjects(Class<? extends C> type, ObjectQuery query, Collection<SelectorOptions<GetOperationOptions>> currentOptions, Task task, OperationResult result)
                    throws CommunicationException, ObjectNotFoundException, SchemaException, SecurityViolationException, ConfigurationException, ExpressionEvaluationException {
                Collection<SelectorOptions<GetOperationOptions>> defaultOptions = DefaultColumnUtils.createOption(getObjectCollectionView().getTargetClass(getPrismContext()), getSchemaService());
                QName qNameType = WebComponentUtil.containerClassToQName(getPrismContext(), type);
                VariablesMap variables = new VariablesMap();
                if (getSearchModel().getObject() != null) {
                    variables.putAll(getSearchModel().getObject().getFilterVariables(getVariables(), getPageBase()));
                    processReferenceVariables(variables);
                }
                return getModelInteractionService().countObjectsFromCollection(getReport().getObjectCollection().getCollection(), qNameType, defaultOptions, null, variables, task, result);
            }

            @Override
            public ObjectQuery getQuery() {
                //fake query because of we need paging in method createDataObjectWrappers
                return getPrismContext().queryFor(ObjectType.class).build();
            }
        };
        if (provider.getSort() == null && ObjectType.class.isAssignableFrom(getDefaultType())) {
            provider.setSort("name", SortOrder.ASCENDING);
        }
        return provider;
    }

    private void processVariables(VariablesMap variablesMap) {
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            if (!variablesMap.containsKey(entry.getKey())) {
                if (entry.getValue() == null) {
                    variablesMap.put(entry.getKey(), null, String.class);
                } else if (entry.getValue() instanceof Item) {
                    variablesMap.put(entry.getKey(), (Item)entry.getValue(), ((Item)entry.getValue()).getDefinition());
                } else {
                    variablesMap.put(entry.getKey(), entry.getValue(), entry.getValue().getClass());
                }
            }
        }
        processReferenceVariables(variablesMap);
    }

    @Override
    protected SearchFormPanel initSearch(String headerId) {
        return new SearchFormPanel<>(headerId, getSearchModel()) {
            @Override
            protected void searchPerformed(AjaxRequestTarget target) {
                refreshTable(target);
            }
        };
    }

    @Override
    protected Search createSearch(Class<C> type) {
        return SearchFactory.createSearchForReport(type, getReport().getObjectCollection().getParameter(), getPageBase());
    }

    @Override
    protected IColumn<SelectableBean<C>, String> createNameColumn(IModel<String> displayModel, GuiObjectColumnType customColumn, String itemPath, ExpressionType expression) {
        return createCustomExportableColumn(displayModel, customColumn, itemPath == null ? null : ItemPath.create(itemPath), expression);
    }

    @Override
    protected List<IColumn<SelectableBean<C>, String>> createDefaultColumns() {
        return null;
    }

    @Override
    protected void customProcessNewRowItem(org.apache.wicket.markup.repeater.Item item, IModel<SelectableBean<C>> model) {
        if (model == null || model.getObject() == null || model.getObject().getValue() == null) {
            return;
        }
        VariablesMap variables = getSearchModel().getObject().getFilterVariables(null, getPageBase());
        variables.put(ExpressionConstants.VAR_OBJECT, model.getObject().getValue(), model.getObject().getValue().getClass());
        if (report.getObject() != null && report.getObject().getObjectCollection() != null
                && report.getObject().getObjectCollection().getSubreport() != null
                && !report.getObject().getObjectCollection().getSubreport().isEmpty()) {
            Task task = getPageBase().createSimpleTask("evaluate subreports");
            processReferenceVariables(variables);
            VariablesMap subreportsVariables = getPageBase().getReportManager().evaluateSubreportParameters(report.getObject().asPrismObject(), variables, task, task.getResult());
            variables.putAll(subreportsVariables);
        }
        this.variables.clear();
        for (String key : variables.keySet()) {
            this.variables.put(key, variables.get(key).getValue());
        }
    }

    private void processReferenceVariables(VariablesMap variablesMap) {
        if (variablesMap.isEmpty()) {
            return;
        }
        List<String> keysForRemoving = new ArrayList<>();
        variablesMap.keySet().forEach(key -> {
            Object value = variablesMap.get(key).getValue();
            if (value instanceof Referencable && ((Referencable) value).getOid() == null) {
                keysForRemoving.add(key);
            }
        });
        keysForRemoving.forEach((key -> {
            variablesMap.remove(key);
            variablesMap.put(key, null, ObjectReferenceType.class);
        }));
    }

    @Override
    protected Collection evaluateExpression(C rowValue, com.evolveum.midpoint.prism.Item<?, ?> columnItem, ExpressionType expression, GuiObjectColumnType customColumn) {
        Task task = getPageBase().createSimpleTask(OPERATION_EVALUATE_EXPRESSION);
        OperationResult result = task.getResult();
        try {
            VariablesMap variablesMap = new VariablesMap();
            if (columnItem == null) {
                variablesMap.put(ExpressionConstants.VAR_INPUT, null, String.class);
            } else {
                variablesMap.put(ExpressionConstants.VAR_INPUT, columnItem, columnItem.getDefinition());
            }
            processVariables(variablesMap);
            if (!variablesMap.containsKey(ExpressionConstants.VAR_OBJECT)) {
                variablesMap.put(ExpressionConstants.VAR_OBJECT, rowValue, rowValue.asPrismContainerValue().getDefinition());

            }
            Object object = getPageBase().getReportManager().evaluateScript(getReport().asPrismObject(), expression, variablesMap, "evaluate column expression", task, result);
            if (object instanceof Collection) {
                return (Collection)object;
            }
            return Collections.singletonList(object);
        } catch (Exception e) {
            LOGGER.error("Couldn't execute expression for {} column. Reason: {}", customColumn, e.getMessage(), e);
            result.recomputeStatus();
            OperationResultStatusPresentationProperties props = OperationResultStatusPresentationProperties.parseOperationalResultStatus(result.getStatus());
            return Collections.singletonList(getPageBase().createStringResource(props.getStatusLabelKey()).getString());  //TODO: this is not entirely correct
        }
    }

    public VariablesMap getReportVariables() {
        VariablesMap variablesMap = getSearchModel().getObject().getFilterVariables(null, getPageBase());
        processReferenceVariables(variablesMap);
        return variablesMap;
    }

    @Override
    public PageStorage getPageStorage() {
        if (pageStorage == null) {
            pageStorage = new ObjectListStorage();
        }
        return pageStorage;
    }
}
