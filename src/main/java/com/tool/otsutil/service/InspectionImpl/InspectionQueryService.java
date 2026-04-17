package com.tool.otsutil.service.InspectionImpl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tool.otsutil.model.dto.inspection.InspectionPage;
import com.tool.otsutil.model.dto.inspection.JavaInspectionListRequest;
import com.tool.otsutil.model.dto.inspection.ServerHistoryRequest;
import com.tool.otsutil.model.dto.inspection.ServerInspectionListRequest;
import com.tool.otsutil.model.entity.InspectionTable;
import com.tool.otsutil.model.vo.inspection.DashboardSummaryView;
import com.tool.otsutil.model.vo.inspection.JavaInspectionDetailView;
import com.tool.otsutil.model.vo.inspection.JavaInspectionView;
import com.tool.otsutil.model.vo.inspection.JavaProcessDiffView;
import com.tool.otsutil.model.vo.inspection.PageResponse;
import com.tool.otsutil.model.vo.inspection.ResourcePeakView;
import com.tool.otsutil.model.vo.inspection.ServerInspectionView;
import com.tool.otsutil.model.vo.inspection.StatusSummaryView;
import com.tool.otsutil.model.vo.inspection.TopologyCityView;
import com.tool.otsutil.model.vo.inspection.TopologySummaryView;
import com.tool.otsutil.util.InspectionViewSupport;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class InspectionQueryService {

    private final InspectionTableService inspectionTableService;
    private final TuMoStatisticsService tuMoStatisticsService;

    public InspectionQueryService(InspectionTableService inspectionTableService,
                                  TuMoStatisticsService tuMoStatisticsService) {
        this.inspectionTableService = inspectionTableService;
        this.tuMoStatisticsService = tuMoStatisticsService;
    }

    public DashboardSummaryView getDashboardSummary() {
        List<InspectionTable> latestRecords = inspectionTableService.listLatestInspectionTable(null, null, null);
        DashboardSummaryView view = new DashboardSummaryView();

        StatusSummaryView summaryView = new StatusSummaryView();
        summaryView.setServerCount(latestRecords.size());
        for (InspectionTable record : latestRecords) {
            if (record.getStatus() == 2) {
                summaryView.setErrorCount(summaryView.getErrorCount() + 1);
            } else if (record.getStatus() == 1) {
                summaryView.setWarningCount(summaryView.getWarningCount() + 1);
            } else {
                summaryView.setNormalCount(summaryView.getNormalCount() + 1);
            }
        }

        view.setSummary(summaryView);
        view.setCpuPeak(findPeak(latestRecords, new PeakValueExtractor() {
            @Override
            public double getValue(InspectionTable record) {
                return InspectionViewSupport.parseNumeric(record.getCPU_USAGE());
            }
        }));
        view.setMemoryPeak(findPeak(latestRecords, new PeakValueExtractor() {
            @Override
            public double getValue(InspectionTable record) {
                return InspectionViewSupport.parseNumeric(record.getMEMORY_USAGE_RATE());
            }
        }));
        view.setDiskPeak(findPeak(latestRecords, new PeakValueExtractor() {
            @Override
            public double getValue(InspectionTable record) {
                return maxDiskValue(record);
            }
        }));
        view.setLastInspectionTime(latestRecords.stream()
                .map(InspectionTable::getUpdateTime)
                .max(Comparator.naturalOrder())
                .map(InspectionViewSupport::formatDateTime)
                .orElse(null));

        int changedServerCount = 0;
        for (InspectionTable record : latestRecords) {
            List<InspectionTable> recentRecords = inspectionTableService.getRecentInspectionByIp(record.getIP(), 2);
            if (recentRecords.size() < 2) {
                continue;
            }
            JavaProcessDiffView diffView = InspectionViewSupport.diffJavaProcesses(
                    recentRecords.get(0).getJAVA_PROCESSES(),
                    recentRecords.get(1).getJAVA_PROCESSES()
            );
            if (!diffView.getAddedProcesses().isEmpty() || !diffView.getRemovedProcesses().isEmpty()) {
                changedServerCount++;
            }
        }
        view.setJavaChangedServerCount(changedServerCount);
        view.setTopology(buildTopologySummary());
        return view;
    }

    public PageResponse<ServerInspectionView> getServerInspectionList(ServerInspectionListRequest request) {
        InspectionPage pageRequest = new InspectionPage();
        pageRequest.setIP(request.getIp());
        pageRequest.setUpdateTime(request.getDate());
        pageRequest.setStatus(request.getStatus());
        pageRequest.setPage(normalizePage(request.getPage()));
        pageRequest.setPageSize(normalizePageSize(request.getPageSize()));

        Page<InspectionTable> page = inspectionTableService.getPaginatedInspectionTable(pageRequest);
        return toServerPageResponse(page);
    }

    public ServerInspectionView getServerInspectionDetail(String ip, String updateTime) {
        InspectionTable record = inspectionTableService.getInspectionByIp(ip, InspectionViewSupport.parseDateTime(updateTime));
        return toServerView(record);
    }

    public PageResponse<ServerInspectionView> getServerInspectionHistory(ServerHistoryRequest request) {
        InspectionPage pageRequest = new InspectionPage();
        pageRequest.setIP(request.getIp());
        pageRequest.setPage(normalizePage(request.getPage()));
        pageRequest.setPageSize(normalizePageSize(request.getPageSize()));

        Page<InspectionTable> page = inspectionTableService.getInspectionPageByIp(pageRequest);
        return toServerPageResponse(page);
    }

    public PageResponse<JavaInspectionView> getJavaInspectionList(JavaInspectionListRequest request) {
        List<InspectionTable> latestRecords = inspectionTableService.listLatestInspectionTable(request.getIp(), null, request.getStatus());
        latestRecords.sort(new Comparator<InspectionTable>() {
            @Override
            public int compare(InspectionTable left, InspectionTable right) {
                return right.getUpdateTime().compareTo(left.getUpdateTime());
            }
        });

        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, latestRecords.size());

        PageResponse<JavaInspectionView> response = new PageResponse<JavaInspectionView>();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(latestRecords.size());

        if (start >= latestRecords.size()) {
            return response;
        }

        List<JavaInspectionView> records = new ArrayList<JavaInspectionView>();
        for (InspectionTable record : latestRecords.subList(start, end)) {
            records.add(toJavaInspectionView(record));
        }
        response.setRecords(records);
        return response;
    }

    public JavaInspectionDetailView getJavaInspectionDetail(String ip) {
        List<InspectionTable> recentRecords = inspectionTableService.getRecentInspectionByIp(ip, 2);
        if (recentRecords.isEmpty()) {
            return null;
        }

        InspectionTable currentRecord = recentRecords.get(0);
        InspectionTable previousRecord = recentRecords.size() > 1 ? recentRecords.get(1) : null;

        JavaInspectionDetailView view = new JavaInspectionDetailView();
        view.setIp(ip);
        view.setDescription(currentRecord.getDescription());
        view.setCurrentStatus(currentRecord.getStatus());
        view.setCurrentUpdateTime(InspectionViewSupport.formatDateTime(currentRecord.getUpdateTime()));
        view.setCurrentProcesses(InspectionViewSupport.splitJavaProcesses(currentRecord.getJAVA_PROCESSES()));

        if (previousRecord != null) {
            view.setPreviousStatus(previousRecord.getStatus());
            view.setPreviousUpdateTime(InspectionViewSupport.formatDateTime(previousRecord.getUpdateTime()));
            view.setPreviousProcesses(InspectionViewSupport.splitJavaProcesses(previousRecord.getJAVA_PROCESSES()));
        }

        view.setDiff(InspectionViewSupport.diffJavaProcesses(view.getCurrentProcesses(), view.getPreviousProcesses()));
        return view;
    }

    public TopologySummaryView getTopologySummary() {
        return buildTopologySummary();
    }

    private PageResponse<ServerInspectionView> toServerPageResponse(Page<InspectionTable> page) {
        PageResponse<ServerInspectionView> response = new PageResponse<ServerInspectionView>();
        response.setPage(page.getCurrent());
        response.setPageSize(page.getSize());
        response.setTotal(page.getTotal());

        List<ServerInspectionView> records = new ArrayList<ServerInspectionView>();
        for (InspectionTable record : page.getRecords()) {
            records.add(toServerView(record));
        }
        response.setRecords(records);
        return response;
    }

    private ServerInspectionView toServerView(InspectionTable record) {
        if (record == null) {
            return null;
        }

        ServerInspectionView view = new ServerInspectionView();
        view.setIp(record.getIP());
        view.setUpdateTime(InspectionViewSupport.formatDateTime(record.getUpdateTime()));
        view.setStatus(record.getStatus());
        view.setDescription(record.getDescription());
        view.setCpuUsage(record.getCPU_USAGE());
        view.setMemoryUsage(record.getMEMORY_USAGE());
        view.setMemoryTotal(record.getMEMORY_TOTAL());
        view.setMemoryUsageRate(record.getMEMORY_USAGE_RATE());
        view.setDiskUsage(record.getDISK_USAGE());
        view.setDiskTotal(record.getDISK_TOTAL());
        view.setDiskUsageRate(record.getDISK_USAGE_RATE());
        view.setSecondDiskUsage(record.getSECOND_DISK_USAGE());
        view.setSecondDiskTotal(record.getSECOND_DISK_TOTAL());
        view.setSecondDiskUsageRate(record.getSECOND_DISK_USAGE_RATE());
        view.setThirdDiskUsage(record.getTHIRD_DISK_USAGE());
        view.setThirdDiskTotal(record.getTHIRD_DISK_TOTAL());
        view.setThirdDiskUsageRate(record.getTHIRD_DISK_USAGE_RATE());
        view.setThreadCount(record.getTHREAD_COUNT());
        view.setJavaProcesses(InspectionViewSupport.splitJavaProcesses(record.getJAVA_PROCESSES()));
        view.setJavaProcessCount(view.getJavaProcesses().size());
        return view;
    }

    private JavaInspectionView toJavaInspectionView(InspectionTable currentRecord) {
        JavaInspectionView view = new JavaInspectionView();
        view.setIp(currentRecord.getIP());
        view.setUpdateTime(InspectionViewSupport.formatDateTime(currentRecord.getUpdateTime()));
        view.setStatus(currentRecord.getStatus());
        view.setDescription(currentRecord.getDescription());
        view.setJavaProcesses(InspectionViewSupport.splitJavaProcesses(currentRecord.getJAVA_PROCESSES()));
        view.setJavaProcessCount(view.getJavaProcesses().size());

        List<InspectionTable> recentRecords = inspectionTableService.getRecentInspectionByIp(currentRecord.getIP(), 2);
        InspectionTable previousRecord = recentRecords.size() > 1 ? recentRecords.get(1) : null;
        JavaProcessDiffView diffView = InspectionViewSupport.diffJavaProcesses(
                currentRecord.getJAVA_PROCESSES(),
                previousRecord == null ? null : previousRecord.getJAVA_PROCESSES()
        );

        view.setAddedProcesses(diffView.getAddedProcesses());
        view.setRemovedProcesses(diffView.getRemovedProcesses());
        view.setHasDiff(!diffView.getAddedProcesses().isEmpty() || !diffView.getRemovedProcesses().isEmpty());
        return view;
    }

    private TopologySummaryView buildTopologySummary() {
        LocalDate previousDate = LocalDate.now().minusDays(1);
        String sourceDate = previousDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String displayDate = previousDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        Map<String, Integer> zyStatistics = tuMoStatisticsService.getZyStatistics(sourceDate);
        Map<String, Integer> dyStatistics = tuMoStatisticsService.getDyStatistics(sourceDate);
        Map<String, String> cityMap = tuMoStatisticsService.getCityMap();

        TopologySummaryView view = new TopologySummaryView();
        view.setDate(displayDate);

        for (Map.Entry<String, String> cityEntry : cityMap.entrySet()) {
            String cityCode = cityEntry.getKey();
            TopologyCityView cityView = new TopologyCityView();
            cityView.setCityCode(cityCode);
            cityView.setCityName(cityEntry.getValue());
            cityView.setZyCount(zyStatistics.getOrDefault(cityCode, 0));
            cityView.setDyCount(dyStatistics.getOrDefault(cityCode, 0));
            view.getCities().add(cityView);
            view.setZyTotal(view.getZyTotal() + cityView.getZyCount());
            view.setDyTotal(view.getDyTotal() + cityView.getDyCount());
        }

        return view;
    }

    private ResourcePeakView findPeak(List<InspectionTable> records, PeakValueExtractor extractor) {
        InspectionTable peakRecord = null;
        double peakValue = 0D;

        for (InspectionTable record : records) {
            double currentValue = extractor.getValue(record);
            if (peakRecord == null || currentValue > peakValue) {
                peakRecord = record;
                peakValue = currentValue;
            }
        }

        if (peakRecord == null) {
            return null;
        }

        ResourcePeakView view = new ResourcePeakView();
        view.setIp(peakRecord.getIP());
        view.setStatus(peakRecord.getStatus());
        view.setValue(peakValue);
        view.setUpdateTime(InspectionViewSupport.formatDateTime(peakRecord.getUpdateTime()));
        return view;
    }

    private double maxDiskValue(InspectionTable record) {
        double diskOne = InspectionViewSupport.parseNumeric(record.getDISK_USAGE_RATE());
        double diskTwo = InspectionViewSupport.parseNumeric(record.getSECOND_DISK_USAGE_RATE());
        double diskThree = InspectionViewSupport.parseNumeric(record.getTHIRD_DISK_USAGE_RATE());
        return Math.max(diskOne, Math.max(diskTwo, diskThree));
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    private interface PeakValueExtractor {
        double getValue(InspectionTable record);
    }
}
