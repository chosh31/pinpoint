/*
 * Copyright 2014 NAVER Corp.
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

package com.navercorp.pinpoint.web.alarm.collector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.navercorp.pinpoint.web.alarm.DataCollectorFactory.DataCollectorCategory;
import com.navercorp.pinpoint.web.dao.AgentStatDao;
import com.navercorp.pinpoint.web.dao.ApplicationIndexDao;
import com.navercorp.pinpoint.web.vo.AgentStat;
import com.navercorp.pinpoint.web.vo.Application;
import com.navercorp.pinpoint.web.vo.Range;

/**
 * @author minwoo.jung
 */
public class AgentStatDataCollector extends DataCollector {

    private final Application application;
    private final AgentStatDao agentStatDao;
    private final ApplicationIndexDao applicationIndexDao;
    private final long timeSlotEndTime;
    private final long slotInterval;
    private final AtomicBoolean init = new AtomicBoolean(false); // need to consider a race condition when checkers start simultaneously.

    private final Map<String, Long> agentHeapUsageRate = new HashMap<String, Long>();
    private final Map<String, Long> agentGcCount = new HashMap<String, Long>();
    private final Map<String, Long> agentJvmCpuUsageRate = new HashMap<String, Long>();

    public AgentStatDataCollector(DataCollectorCategory category, Application application, AgentStatDao agentStatDao, ApplicationIndexDao applicationIndexDao, long timeSlotEndTime, long slotInterval) {
        super(category);
        this.application = application;
        this.agentStatDao = agentStatDao;
        this.applicationIndexDao = applicationIndexDao;
        this.timeSlotEndTime = timeSlotEndTime;
        this.slotInterval = slotInterval;
    }

    @Override
    public void collect() {
        if (init.get()) {
            return;
        }

        Range range = Range.createUncheckedRange(timeSlotEndTime - slotInterval, timeSlotEndTime);
        List<String> agentIds = applicationIndexDao.selectAgentIds(application.getName());

        for(String agentId : agentIds) {
            List<AgentStat> scanAgentStatList = agentStatDao.scanAgentStatList(agentId, range);
            int listSize = scanAgentStatList.size();
            long totalHeapSize = 0;
            long usedHeapSize = 0;
            long jvmCpuUsaged = 0;

            for (AgentStat agentStat : scanAgentStatList) {
                totalHeapSize += agentStat.getMemoryGc().getJvmMemoryHeapMax();
                usedHeapSize += agentStat.getMemoryGc().getJvmMemoryHeapUsed();

                jvmCpuUsaged += agentStat.getCpuLoad().getJvmCpuLoad() * 100;
            }

            if(listSize > 0) {
                long percent = calculatePercent(usedHeapSize, totalHeapSize);
                agentHeapUsageRate.put(agentId, percent);

                percent = calculatePercent(jvmCpuUsaged, 100*scanAgentStatList.size());
                agentJvmCpuUsageRate.put(agentId, percent);

                long accruedLastGCcount = scanAgentStatList.get(0).getMemoryGc().getJvmGcOldCount();
                long accruedFirstGCcount= scanAgentStatList.get(listSize - 1).getMemoryGc().getJvmGcOldCount();
                agentGcCount.put(agentId, accruedLastGCcount - accruedFirstGCcount);
            }

        }

        init.set(true);

    }

    private long calculatePercent(long used, long total) {
        if (total == 0 || used == 0) {
            return 0;
        } else {
            return (used * 100L) / total;
        }
    }

    public Map<String, Long> getHeapUsageRate() {
        return agentHeapUsageRate;
    }

    public Map<String, Long> getGCCount() {
        return agentGcCount;
    }

    public Map<String, Long> getJvmCpuUsageRate() {
        return agentJvmCpuUsageRate;
    }

}
