package io.mosip.resident.dto;

import lombok.Data;

@Data
public class PartnerResponseDto {

	private String partnerId;

	private String status;

	private String policyGroupId;
	
	private String policyGroupName;

	private String orgName;

	private String emailAddress;

	private String partnerType;

	private Boolean isActive;
	
	private String certificateUploadStatus;
	
	private String createdDateTime;

}
