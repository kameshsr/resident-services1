package io.mosip.resident.util;

import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.resident.config.LoggerConfiguration;
import io.mosip.resident.constant.ApiName;
import io.mosip.resident.constant.ResidentConstants;
import io.mosip.resident.constant.ResidentErrorCode;
import io.mosip.resident.exception.ApisResourceAccessException;
import io.mosip.resident.exception.ResidentServiceCheckedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Kamesh Shekhar Prasad
 */

@Component
public class PartnersByPartnerType {

    private static final Logger logger = LoggerConfiguration.logConfig(PartnersByPartnerType.class);

    @Autowired
    private ResidentServiceRestClient residentServiceRestClient;

    @Value("${mosip.resident.page.no:0}")
    private String pageNo;

    @Value("${mosip.resident.page.size:25}")
    private String pageSize;

    /** Response key holding the list of partners in a single page. */
    private static final String RESPONSE_DATA_KEY = "data";

    /** Response key holding the total number of partners across all pages. */
    private static final String TOTAL_RESULTS_KEY = "totalResults";

    /** Page size used when the configured value is not a valid number. */
    private static final int DEFAULT_PAGE_SIZE = 100;

    /** Safety guard to avoid an unbounded loop if the API keeps returning data. */
    private static final int MAX_PAGES = 1000;

    public ResponseWrapper<?> getPartnersByPartnerType(Optional<String> partnerType, ApiName apiUrl) throws ResidentServiceCheckedException {
        return getPartnersByPartnerIdAndType(null, partnerType, apiUrl);
    }

    public ResponseWrapper<?> getPartnersByPartnerIdAndType(String partnerId, Optional<String> partnerType, ApiName apiUrl)
            throws ResidentServiceCheckedException {
        logger.debug("GetPartnersByPartnerType::getPartnersByPartnerType()::entry");

        ResponseWrapper<Object> mergedResponseWrapper = new ResponseWrapper<>();
        List<Object> mergedPartners = new ArrayList<>();
        Map<String, Object> lastPageResponse = null;

        int effectivePageSize = parseIntOrDefault(pageSize, DEFAULT_PAGE_SIZE);
        int currentPageNo = parseIntOrDefault(pageNo, 0);
        int totalResults = 0;
        int pageCounter = 0;
        boolean continuePaging = true;

        try {
            do {
                ResponseWrapper<?> pageResponseWrapper = callPartnerApi(partnerId, partnerType, apiUrl,
                        currentPageNo, effectivePageSize);

                if (pageResponseWrapper.getErrors() != null && !pageResponseWrapper.getErrors().isEmpty()) {
                    logger.error(pageResponseWrapper.getErrors().get(0).toString());
                    throw new ResidentServiceCheckedException(pageResponseWrapper.getErrors().get(0).getErrorCode(),
                            pageResponseWrapper.getErrors().get(0).getMessage());
                }

                Object responseObj = pageResponseWrapper.getResponse();
                if (!(responseObj instanceof Map)) {
                    // Response is not a paginated map. On the very first call, return it
                    // unchanged to preserve the original (non-paginated) contract.
                    if (mergedPartners.isEmpty()) {
                        logger.debug("GetPartnersByPartnerType::getPartnersByPartnerType()::exit (non-paginated response)");
                        return pageResponseWrapper;
                    }
                    break;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> pageResponse = (Map<String, Object>) responseObj;
                lastPageResponse = pageResponse;

                Object totalResultsObj = pageResponse.get(TOTAL_RESULTS_KEY);
                if (totalResultsObj instanceof Number) {
                    totalResults = ((Number) totalResultsObj).intValue();
                }

                Object dataObj = pageResponse.get(RESPONSE_DATA_KEY);
                if (!(dataObj instanceof List) || ((List<?>) dataObj).isEmpty()) {
                    break;
                }

                @SuppressWarnings("unchecked")
                List<Object> pageData = (List<Object>) dataObj;
                mergedPartners.addAll(pageData);

                currentPageNo++;
                pageCounter++;

                // Decide whether to fetch another page. Don't rely solely on
                // totalResults: if the API omits it (defaults to 0) we would
                // otherwise stop after page 1 and silently truncate the result.
                boolean reachedKnownTotal = totalResults > 0 && mergedPartners.size() >= totalResults;
                boolean partialPage = pageData.size() < effectivePageSize;
                continuePaging = !reachedKnownTotal && !partialPage;
            } while (continuePaging && pageCounter < MAX_PAGES);

        } catch (ApisResourceAccessException e) {
            logger.error("Error occured in accessing partners list from partner management API: {}", e.getMessage(), e);
            throw new ResidentServiceCheckedException(ResidentErrorCode.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode(),
                    ResidentErrorCode.API_RESOURCE_ACCESS_EXCEPTION.getErrorMessage(), e);
        }

        Map<String, Object> mergedResponse = new LinkedHashMap<>();
        if (lastPageResponse != null) {
            mergedResponse.putAll(lastPageResponse);
        }
        mergedResponse.put(ResidentConstants.PAGE_NO, 0);
        mergedResponse.put(ResidentConstants.PAGE_SIZE, mergedPartners.size());
        mergedResponse.put(TOTAL_RESULTS_KEY, mergedPartners.size());
        mergedResponse.put(RESPONSE_DATA_KEY, mergedPartners);
        mergedResponseWrapper.setResponse(mergedResponse);

        logger.debug("GetPartnersByPartnerType::getPartnersByPartnerType()::exit, merged partners size: {}",
                mergedPartners.size());
        return mergedResponseWrapper;
    }

    /**
     * Fetches a single page of partners from the partner management API.
     */
    private ResponseWrapper<?> callPartnerApi(String partnerId, Optional<String> partnerType, ApiName apiUrl,
            int requestPageNo, int requestPageSize) throws ApisResourceAccessException {
        List<String> pathsegements = null;
        List<String> queryParamName = new ArrayList<>();
        List<Object> queryParamValue = new ArrayList<>();

        // Pagination params are always sent so we can iterate over all pages.
        queryParamName.add(ResidentConstants.PAGE_NO);
        queryParamValue.add(requestPageNo);
        queryParamName.add(ResidentConstants.PAGE_SIZE);
        queryParamValue.add(requestPageSize);

        if (partnerType.isPresent()) {
            queryParamName.add(ResidentConstants.PARTNER_TYPE);
            queryParamValue.add(partnerType.get());
        }
        if (partnerId != null) {
            queryParamName.add(ResidentConstants.PARTNER_ID);
            queryParamValue.add(partnerId);
        }

        return (ResponseWrapper<?>) residentServiceRestClient.getApi(apiUrl,
                pathsegements, queryParamName, queryParamValue, ResponseWrapper.class);
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
