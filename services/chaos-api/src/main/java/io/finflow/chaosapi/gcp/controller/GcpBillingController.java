package io.finflow.chaosapi.gcp.controller;

import io.finflow.chaosapi.gcp.dto.GcpBillingExportResponse;
import io.finflow.chaosapi.gcp.dto.GcpBillingRow;
import io.finflow.chaosapi.gcp.dto.SyntheticGcpData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/gcp")
public class GcpBillingController{
    public static final Logger log = LoggerFactory.getLogger(GcpBillingController.class);
    public static final int  PAGE_SIZE = 4;

    public final SyntheticGcpData data;

    public GcpBillingController(SyntheticGcpData data){
        this.data = data;
    }

    @GetMapping("/billing-export")
    public GcpBillingExportResponse billingExport(
            @RequestParam(required = false) String nextPageToken) {

        List<GcpBillingRow> all = data.all();
        int offset = decodeToken(nextPageToken, all.size());
        int end = Math.min(offset + PAGE_SIZE, all.size());

        List<GcpBillingRow> page = all.subList(offset, end);
        String newToken = (end < all.size()) ? String.valueOf(end) : null;
        log.info("[GCP-CHAOS] billing-export offset={} -> {} rows, nextPageToken={}",
                offset, page.size(), newToken);

        return new GcpBillingExportResponse(page, newToken);
    }

    private int decodeToken(String token, int size) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        try {
            int offset = Integer.parseInt(token.trim());
            return (offset < 0 || offset > size) ? 0 : offset;
        } catch (NumberFormatException e) {
            log.warn("[GCP-CHAOS] bad nextPageToken '{}', starting at 0", token);
            return 0;
        }
    }
}