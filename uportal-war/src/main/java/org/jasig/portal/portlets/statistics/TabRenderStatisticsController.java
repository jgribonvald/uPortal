/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.portal.portlets.statistics;

import com.google.visualization.datasource.base.TypeMismatchException;
import com.google.visualization.datasource.datatable.ColumnDescription;
import com.google.visualization.datasource.datatable.value.NumberValue;
import com.google.visualization.datasource.datatable.value.Value;
import com.google.visualization.datasource.datatable.value.ValueType;
import org.jasig.portal.events.aggr.AggregationInterval;
import org.jasig.portal.events.aggr.BaseAggregationDao;
import org.jasig.portal.events.aggr.BaseAggregationDateTimeComparator;
import org.jasig.portal.events.aggr.groups.AggregatedGroupLookupDao;
import org.jasig.portal.events.aggr.groups.AggregatedGroupMapping;
import org.jasig.portal.events.aggr.tabrender.TabRenderAggregation;
import org.jasig.portal.events.aggr.tabrender.TabRenderAggregationDao;
import org.jasig.portal.events.aggr.tabrender.TabRenderAggregationDiscriminator;
import org.jasig.portal.events.aggr.tabrender.TabRenderAggregationDiscriminatorImpl;
import org.jasig.portal.events.aggr.tabrender.TabRenderAggregationKey;
import org.jasig.portal.events.aggr.tabrender.TabRenderAggregationKeyImpl;
import org.jasig.portal.events.aggr.tabs.AggregatedTabLookupDao;
import org.jasig.portal.events.aggr.tabs.AggregatedTabMapping;
import org.jasig.portal.events.aggr.tabs.AggregatedTabMappingNameComparator;
import org.jasig.portal.utils.ComparableExtractingComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.portlet.ModelAndView;
import org.springframework.web.portlet.bind.annotation.RenderMapping;
import org.springframework.web.portlet.bind.annotation.ResourceMapping;

import java.util.*;

/**
 * Tab render reports
 * 
 * @author James Wennmacher, jameswennmacher@gmail.com
 */
@Controller
@RequestMapping(value="VIEW")
public class TabRenderStatisticsController extends
        BaseStatisticsReportController<TabRenderAggregation, TabRenderAggregationKey,
                TabRenderAggregationDiscriminator, TabRenderReportForm> {
    private static final String DATA_TABLE_RESOURCE_ID = "tabRenderData";
    private final static String REPORT_NAME = "tabRender.totals";

    @Autowired
    private TabRenderAggregationDao<TabRenderAggregation> tabRenderDao;

    @Autowired
    private AggregatedGroupLookupDao aggregatedGroupDao;

    @Autowired
    private AggregatedTabLookupDao aggregatedTabLookupDao;
    
    @RenderMapping(value="MAXIMIZED", params="report=" + REPORT_NAME)
    public String getLoginView() throws TypeMismatchException {
        return "jsp/Statistics/reportGraph";
    }
    
    @ResourceMapping(DATA_TABLE_RESOURCE_ID)
    public ModelAndView renderTabRenderAggregationReport(TabRenderReportForm form) throws TypeMismatchException {
        return renderAggregationReport(form);
    }
    
    @Override
    protected TabRenderReportForm createReportFormRequest() {
        TabRenderReportForm form = new TabRenderReportForm();
        setReportFormTabs(form);
        return form;
    }

    /**
     * Set the tab names to have selected by default
     */
    protected void setReportFormTabs(final TabRenderReportForm report) {
        final Set<AggregatedTabMapping> tabs = this.getTabs();
        if (!tabs.isEmpty()) {
            report.getTabs().add(tabs.iterator().next().getId());
        }
    }

    /**
     * @return Tabs that exist for the aggregation
     */
    @ModelAttribute("tabs")
    public Set<AggregatedTabMapping> getTabs() {
        final Set<AggregatedTabMapping> groupMappings = aggregatedTabLookupDao.getTabMappings();

        final Set<AggregatedTabMapping> sortedGroupMappings = new TreeSet<AggregatedTabMapping>(AggregatedTabMappingNameComparator.INSTANCE);
        sortedGroupMappings.addAll(groupMappings);
        return sortedGroupMappings;
    }

    @Override
    public String getReportName() {
        return REPORT_NAME;
    }

    @Override
    public String getReportDataResourceId() {
        return DATA_TABLE_RESOURCE_ID;
    }

    @Override
    protected BaseAggregationDao<TabRenderAggregation, TabRenderAggregationKey> getBaseAggregationDao() {
        return this.tabRenderDao;
    }

    @Override
    protected Set<TabRenderAggregationKey> createAggregationsQueryKeyset(
            Set<TabRenderAggregationDiscriminator> columnDiscriminators, TabRenderReportForm form) {
        // Create keys (that exclude the temporal date/time information) from the interval
        // and the data in the column discriminators.
        final AggregationInterval interval = form.getInterval();
        final HashSet<TabRenderAggregationKey> keys = new HashSet<TabRenderAggregationKey>();
        for (TabRenderAggregationDiscriminator discriminator : columnDiscriminators) {
            keys.add(new TabRenderAggregationKeyImpl(interval, discriminator.getAggregatedGroup(),
                    discriminator.getTabMapping()));
        }
        return keys;
    }

    @Override
    protected ComparableExtractingComparator<?, ?> getDiscriminatorComparator() {
        return TabRenderAggregationDiscriminatorImpl.Comparator.INSTANCE;
    }

    protected Map<TabRenderAggregationDiscriminator, SortedSet<TabRenderAggregation>> createColumnDiscriminatorMap
            (TabRenderReportForm form) {
        //Collections used to track the queried groups and the results
        final Map<TabRenderAggregationDiscriminator, SortedSet<TabRenderAggregation>> groupedAggregations =
                new TreeMap<TabRenderAggregationDiscriminator,
                        SortedSet<TabRenderAggregation>>(TabRenderAggregationDiscriminatorImpl.Comparator.INSTANCE);

        //Get concrete group mapping objects that are being queried for
        List<Long> groups = form.getGroups();
        List<Long> tabs = form.getTabs();
        for (final Long queryGroupId : groups) {
            AggregatedGroupMapping groupMapping = this.aggregatedGroupDao.getGroupMapping(queryGroupId);
            for (final Long tabId : tabs) {
                AggregatedTabMapping tabMapping = this.aggregatedTabLookupDao.getTabMapping(tabId);
                final TabRenderAggregationDiscriminator mapping =
                        new TabRenderAggregationDiscriminatorImpl(groupMapping, tabMapping);
                //Create the set the aggregations for this report column will be stored in, sorted chronologically
                final SortedSet<TabRenderAggregation> aggregations =
                        new TreeSet<TabRenderAggregation>(BaseAggregationDateTimeComparator.INSTANCE);

                //Map the group to the set
                groupedAggregations.put(mapping, aggregations);
            }

        }

        return groupedAggregations;
    }

    @Override
    protected List<ColumnDescription> getColumnDescriptions(TabRenderAggregationDiscriminator reportColumnDiscriminator,
                                                            TabRenderReportForm form) {
        AggregatedTabMapping tab = reportColumnDiscriminator.getTabMapping();
        AggregatedGroupMapping group = reportColumnDiscriminator.getAggregatedGroup();
        String groupNameToIncludeInHeader = form.getGroups().size() > 1 ?
                " - " + reportColumnDiscriminator.getAggregatedGroup().getGroupName() : "";

        final List<ColumnDescription> columnDescriptions = new ArrayList<ColumnDescription>();
        columnDescriptions.add(new ColumnDescription(tab.getDisplayString() + groupNameToIncludeInHeader,
                ValueType.NUMBER, tab.getDisplayString() + groupNameToIncludeInHeader));
        return columnDescriptions;
    }

    @Override
    protected List<Value> createRowValues(TabRenderAggregation aggr, TabRenderReportForm form) {
        int count = aggr != null ? aggr.getRenderCount() : 0;
        return Collections.<Value>singletonList(new NumberValue(count));
    }
}