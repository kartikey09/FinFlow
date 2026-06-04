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