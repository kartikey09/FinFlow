package io.finflow.chaosapi.aws;

import io.finflow.chaosapi.aws.dto.AwsCurLineItem;
import io.finflow.chaosapi.aws.dto.CostAndUsageReportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/aws")
public class AwsBillingController {
    private static final Logger log = LoggerFactory.getLogger(AwsBillingController.class);
    private static final int PAGE_SIZE = 5;
    private final SyntheticCurData data;

    //Constructor DI - memory cache which we built - SyntheticCurData.java
    public AwsBillingController(SyntheticCurData data){
        this.data = data;
    }


    /**
     * The primary endpoint for serving the mock AWS billing data.
     *
     * This method receives an optional "nextToken" from the URL, calculates which chunk
     * of the total items to serve (fixed page size), and extracts that
     * It then determines if a subsequent page exists to generate a new token, or returns null if last page
     *
     * Why - Graceful Delivery
     * Returning massive datasets all at once can crash systems in the real world. By
     * returning a small subList and a nextToken, this perfectly mimics standard cloud API
     * pagination, forcing the downstream ingestor to request the data in manageable pieces.
     */

    @GetMapping("/cost-and-usage-report")
    public CostAndUsageReportResponse costAndUsageReportResponse(
           @RequestParam(required = false) String nextToken){ //required=false makes it optional
        // for the fisrt call - it arrives with no token

        List<AwsCurLineItem> all = data.all();  //memory cache list using .all method
        int offset = decodeToken(nextToken, all.size());  //from where to start reading

        //to prevent the pagination crash
        int end = Math.min(offset + PAGE_SIZE, all.size());

        List<AwsCurLineItem> page = all.sublist(offset,end);

        String newToken = (end<all.size())? String.valueOf(end) : null;   //next offset if we have not reached the end otherwise null

        log.info("[AWS-CHAOS] CUR request offset={} -> {} items, nextToken={}", offset, page.size(), newToken);

        return new CostAndUsageReportResponse("2025-11", page, newToken);
    }


    /**
     * It takes the incoming token and attempts to parse it into an integer. If the token
     * is missing, empty, not a valid number (like text), or outside the bounds of the
     * available data, it safely defaults to returning 0 (the beginning of the list).
     *
     * Why - Defensive Programming
     * A reliable API should never crash with a 500 Internal Server Error just because a
     * user typed something unexpected in the URL. This try-catch block ensures the endpoint
     * degrades gracefully and always returns valid data, no matter what garbage input it receives.
     */
    private int decodeToken(String token, int size){
        if (token == null || token.isBlank()) {
            return 0;  //for the very first request - start from 0 (beginning)
        }
        try{
            int offset = Integer.parseInt(token.trim());
            return (offset<0 || offset>size) ? 0 : offset;
        } catch(NumberFormatException e){   //if user sends text instead of number
            log.warn("[AWS-CHAOS] bad nextToken '{}', starting at 0", token);
            return 0;
        }
    }
}