package org.openlmis.requisition.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.refEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openlmis.requisition.domain.RequisitionStatus.APPROVED;
import static org.openlmis.requisition.domain.RequisitionStatus.AUTHORIZED;
import static org.openlmis.requisition.domain.RequisitionStatus.INITIATED;
import static org.openlmis.requisition.domain.RequisitionStatus.RELEASED;
import static org.openlmis.requisition.domain.RequisitionStatus.SKIPPED;
import static org.openlmis.requisition.domain.RequisitionStatus.SUBMITTED;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.requisition.domain.Requisition;
import org.openlmis.requisition.domain.RequisitionLineItem;
import org.openlmis.requisition.domain.RequisitionStatus;
import org.openlmis.requisition.domain.RequisitionTemplate;
import org.openlmis.requisition.dto.ConvertToOrderDto;
import org.openlmis.requisition.dto.FacilityDto;
import org.openlmis.requisition.dto.OrderDto;
import org.openlmis.requisition.dto.ProcessingPeriodDto;
import org.openlmis.requisition.dto.ProcessingScheduleDto;
import org.openlmis.requisition.dto.ProgramDto;
import org.openlmis.requisition.dto.RequisitionDto;
import org.openlmis.requisition.dto.SupervisoryNodeDto;
import org.openlmis.requisition.dto.SupplyLineDto;
import org.openlmis.requisition.dto.UserDto;
import org.openlmis.requisition.exception.InvalidRequisitionStateException;
import org.openlmis.requisition.exception.InvalidRequisitionStatusException;
import org.openlmis.requisition.exception.RequisitionConversionException;
import org.openlmis.requisition.exception.RequisitionException;
import org.openlmis.requisition.exception.RequisitionInitializationException;
import org.openlmis.requisition.exception.RequisitionNotFoundException;
import org.openlmis.requisition.repository.RequisitionRepository;
import org.openlmis.requisition.service.fulfillment.OrderFulfillmentService;
import org.openlmis.requisition.service.referencedata.ApprovedProductReferenceDataService;
import org.openlmis.requisition.service.referencedata.FacilityReferenceDataService;
import org.openlmis.requisition.service.referencedata.PeriodReferenceDataService;
import org.openlmis.requisition.service.referencedata.ProgramReferenceDataService;
import org.openlmis.requisition.service.referencedata.ScheduleReferenceDataService;
import org.openlmis.requisition.service.referencedata.SupervisoryNodeReferenceDataService;
import org.openlmis.requisition.service.referencedata.SupplyLineReferenceDataService;
import org.openlmis.requisition.service.referencedata.UserFulfillmentFacilitiesReferenceDataService;
import org.openlmis.requisition.service.referencedata.UserSupervisedProgramsReferenceDataService;
import org.openlmis.settings.service.ConfigurationSettingService;
import org.openlmis.utils.ConvertHelper;
import org.openlmis.utils.PaginationHelper;
import org.openlmis.utils.RequisitionDtoComparator;
import org.springframework.context.MessageSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings({"PMD.TooManyMethods", "PMD.UnusedPrivateField"})
@RunWith(MockitoJUnitRunner.class)
public class RequisitionServiceTest {

  private Requisition requisition;

  @Mock
  private ProgramDto program;

  @Mock
  private SupervisoryNodeDto supervisoryNode;

  @Mock
  private ConfigurationSettingService configurationSettingService;

  @Mock
  private RequisitionRepository requisitionRepository;

  @Mock
  private ProgramReferenceDataService programReferenceDataService;

  @Mock
  private SupervisoryNodeReferenceDataService supervisoryNodeReferenceDataService;

  @Mock
  private FacilityReferenceDataService facilityReferenceDataService;

  @Mock
  private RequisitionTemplateService requisitionTemplateService;

  @Mock
  private PeriodService periodService;

  @Mock
  private ScheduleReferenceDataService scheduleReferenceDataService;

  @Mock
  private ApprovedProductReferenceDataService approvedProductReferenceDataService;

  @Mock
  private UserSupervisedProgramsReferenceDataService userSupervisedProgramsReferenceDataService;

  @Mock
  private UserFulfillmentFacilitiesReferenceDataService fulfillmentFacilitiesReferenceDataService;

  @Mock
  private PeriodReferenceDataService periodReferenceDataService;

  @Mock
  private SupplyLineReferenceDataService supplyLineService;

  @Mock
  private OrderFulfillmentService orderFulfillmentService;

  @Mock
  private NotificationService notificationService;

  @Mock
  private ConvertHelper convertHelper;

  @Mock
  private PaginationHelper paginationHelper;

  @Mock
  private MessageSource messageSource;

  @InjectMocks
  private RequisitionService requisitionService;

  private ProcessingPeriodDto processingPeriodDto;
  private UUID programId = UUID.randomUUID();
  private UUID facilityId = UUID.randomUUID();
  private UUID suggestedPeriodId = UUID.randomUUID();

  @Before
  public void setUp() throws RequisitionException {
    generateRequisition();
    mockRepositories();
  }

  @Test
  public void shouldDeleteRequisitionIfItIsInitiated() throws RequisitionException {
    requisition.setStatus(INITIATED);
    requisitionService.delete(requisition.getId());
  }

  @Test(expected = InvalidRequisitionStatusException.class)
  public void shouldNotDeleteRequisitionWhenStatusIsSubmitted() throws RequisitionException {
    requisition.setStatus(SUBMITTED);
    requisitionService.delete(requisition.getId());
  }

  @Test(expected = RequisitionException.class)
  public void shouldThrowExceptionWhenDeletingNotExistingRequisition()
      throws RequisitionException {
    UUID deletedRequisitionId = requisition.getId();
    when(requisitionRepository
        .findOne(requisition.getId()))
        .thenReturn(null);
    requisitionService.delete(deletedRequisitionId);
  }

  @Test
  public void shouldSkipRequisitionIfItIsValid() throws RequisitionException {
    when(program.getPeriodsSkippable()).thenReturn(true);
    Requisition skippedRequisition = requisitionService.skip(requisition.getId());

    assertEquals(skippedRequisition.getStatus(), SKIPPED);
  }

  @Test(expected = RequisitionException.class)
  public void shouldThrowExceptionWhenSkippingNotSkippableProgram()
      throws RequisitionException {
    when(program.getPeriodsSkippable()).thenReturn(false);
    requisitionService.skip(requisition.getId());
  }

  @Test(expected = RequisitionException.class)
  public void shouldThrowExceptionWhenSkippingNotExistingRequisition()
      throws RequisitionException {
    when(requisitionRepository
        .findOne(requisition.getId()))
        .thenReturn(null);
    requisitionService.skip(requisition.getId());
  }

  @Test
  public void shouldRejectRequisitionIfRequisitionStatusIsAuthorized() throws RequisitionException {
    requisition.setStatus(AUTHORIZED);
    Requisition returnedRequisition = requisitionService.reject(requisition.getId());

    assertEquals(returnedRequisition.getStatus(), INITIATED);
  }

  @Test(expected = InvalidRequisitionStatusException.class)
  public void shouldThrowExceptionWhenRejectingRequisitionWithStatusSubmitted()
      throws RequisitionException {
    requisition.setStatus(SUBMITTED);
    requisitionService.reject(requisition.getId());
  }

  @Test(expected = InvalidRequisitionStatusException.class)
  public void shouldThrowExceptionWhenRejectingRequisitionWithStatusApproved()
      throws RequisitionException {
    requisition.setStatus(APPROVED);
    requisitionService.reject(requisition.getId());
  }

  @Test(expected = RequisitionNotFoundException.class)
  public void shouldThrowExceptionWhenRejectingNotExistingRequisition()
      throws RequisitionException {
    when(requisitionRepository.findOne(requisition.getId())).thenReturn(null);
    requisitionService.reject(requisition.getId());
  }

  @Test
  public void shouldGetAuthorizedRequisitions() {

    requisition.setStatus(AUTHORIZED);
    requisition.setProgramId(program.getId());

    when(requisitionRepository
        .searchRequisitions(null, program.getId(), null, null, null, null, null, null))
        .thenReturn(Collections.singletonList(requisition));

    List<Requisition> authorizedRequisitions =
        requisitionService.getAuthorizedRequisitions(program);
    List<Requisition> expected = Collections.singletonList(requisition);

    assertEquals(expected, authorizedRequisitions);
  }

  @Test
  public void shouldGetRequisitionsForApprovalIfUserHasSupervisedPrograms() {
    when(program.getId()).thenReturn(programId);
    requisition.setProgramId(programId);
    requisition.setStatus(AUTHORIZED);
    UserDto user = mock(UserDto.class);

    when(userSupervisedProgramsReferenceDataService.getProgramsSupervisedByUser(user.getId()))
        .thenReturn(Arrays.asList(program));
    when(requisitionRepository
        .searchRequisitions(null, programId, null, null, null, null, null, null))
        .thenReturn(Collections.singletonList(requisition));

    List<Requisition> requisitionsForApproval =
        requisitionService.getRequisitionsForApproval(user.getId());

    assertEquals(1, requisitionsForApproval.size());
    assertEquals(requisitionsForApproval.get(0), requisition);
  }

  @Test
  public void shouldInitiateRequisitionIfItDoesNotAlreadyExist()
      throws RequisitionException {
    RequisitionTemplate requisitionTemplate = mock(RequisitionTemplate.class);
    when(requisitionTemplate.hasColumnsDefined()).thenReturn(true);

    when(requisitionRepository
        .findOne(requisition.getId()))
        .thenReturn(null);

    when(facilityReferenceDataService.findOne(facilityId)).thenReturn(mock(FacilityDto.class));
    when(programReferenceDataService.findOne(programId)).thenReturn(mock(ProgramDto.class));
    when(requisitionTemplateService.getTemplateForProgram(programId))
        .thenReturn(requisitionTemplate);

    ProcessingPeriodDto periodDto = new ProcessingPeriodDto();
    periodDto.setStartDate(LocalDate.of(2016, 11, 1));
    periodDto.setEndDate(LocalDate.of(2016, 11, 30));
    when(periodService.findPeriod(programId, facilityId, suggestedPeriodId, false))
        .thenReturn(periodDto);

    UUID userId = UUID.randomUUID();
    Requisition initiatedRequisition = requisitionService.initiate(
        programId, facilityId, suggestedPeriodId, userId, false
    );

    assertEquals(INITIATED, initiatedRequisition.getStatus());
    assertEquals(userId, initiatedRequisition.getInitiatorId());
    assertEquals(1, initiatedRequisition.getNumberOfMonthsInPeriod().longValue());
  }

  @Test(expected = RequisitionInitializationException.class)
  public void shouldThrowExceptionWhenInitiatingEmptyRequisition()
      throws RequisitionException {
    requisitionService.initiate(null, null, null, null, false);
  }

  @Test
  public void shouldReleaseRequisitionsAsOrder() throws RequisitionException {
    // given
    UserDto user = mock(UserDto.class);
    UUID userId = UUID.randomUUID();
    when(user.getId()).thenReturn(userId);

    List<ConvertToOrderDto> requisitions = setUpReleaseRequisitionsAsOrder(5);
    List<FacilityDto> facilities = requisitions.stream()
        .map(r -> facilityReferenceDataService.findOne(r.getSupplyingDepotId()))
        .collect(Collectors.toList());

    when(fulfillmentFacilitiesReferenceDataService.getFulfillmentFacilities(userId))
        .thenReturn(facilities);

    // when
    List<Requisition> expectedRequisitions = requisitionService
        .releaseRequisitionsAsOrder(requisitions, user);

    // then
    for (Requisition requisition : expectedRequisitions) {
      assertEquals(RELEASED, requisition.getStatus());
    }
  }

  @Test(expected = InvalidRequisitionStateException.class)
  public void shouldNotReleaseRequisitionsAsOrderIfSupplyingDepotsNotProvided()
      throws RequisitionException {
    // given
    UserDto user = mock(UserDto.class);
    UUID userId = UUID.randomUUID();
    when(user.getId()).thenReturn(userId);

    List<ConvertToOrderDto> requisitions = setUpReleaseRequisitionsAsOrder(5);
    List<FacilityDto> facilities = requisitions.stream()
        .map(r -> facilityReferenceDataService.findOne(r.getSupplyingDepotId()))
        .collect(Collectors.toList());

    when(fulfillmentFacilitiesReferenceDataService.getFulfillmentFacilities(userId))
        .thenReturn(facilities);

    for (ConvertToOrderDto requisition : requisitions) {
      requisition.setSupplyingDepotId(null);
    }

    // when
    requisitionService.releaseRequisitionsAsOrder(requisitions, user);
  }

  @Test(expected = InvalidRequisitionStateException.class)
  public void shouldNotReleaseRequisitionsAsOrderIfUserHasNoFulfillmentRightsForFacility()
      throws RequisitionException {
    // given
    UserDto user = mock(UserDto.class);
    UUID userId = UUID.randomUUID();
    when(user.getId()).thenReturn(userId);

    List<ConvertToOrderDto> requisitions = setUpReleaseRequisitionsAsOrder(5);

    when(fulfillmentFacilitiesReferenceDataService.getFulfillmentFacilities(userId))
        .thenReturn(new ArrayList<>());

    // when
    requisitionService.releaseRequisitionsAsOrder(requisitions, user);
  }

  @Test
  public void shouldFindRequisitionIfItExists() {
    when(requisitionRepository.searchRequisitions(
        requisition.getFacilityId(),
        requisition.getProgramId(),
        requisition.getCreatedDate().minusDays(2),
        requisition.getCreatedDate().plusDays(2),
        requisition.getProcessingPeriodId(),
        requisition.getSupervisoryNodeId(),
        new RequisitionStatus[]{requisition.getStatus()}, null))
        .thenReturn(Collections.singletonList(requisition));

    List<Requisition> receivedRequisitions = requisitionService.searchRequisitions(
        requisition.getFacilityId(),
        requisition.getProgramId(),
        requisition.getCreatedDate().minusDays(2),
        requisition.getCreatedDate().plusDays(2),
        requisition.getProcessingPeriodId(),
        requisition.getSupervisoryNodeId(),
        new RequisitionStatus[]{requisition.getStatus()}, null);

    assertEquals(1, receivedRequisitions.size());
    assertEquals(
        receivedRequisitions.get(0).getFacilityId(),
        requisition.getFacilityId());
    assertEquals(
        receivedRequisitions.get(0).getProgramId(),
        requisition.getProgramId());
    assertTrue(
        receivedRequisitions.get(0).getCreatedDate().isAfter(
            requisition.getCreatedDate().minusDays(2)));
    assertTrue(
        receivedRequisitions.get(0).getCreatedDate().isBefore(
            requisition.getCreatedDate().plusDays(2)));
    assertEquals(
        receivedRequisitions.get(0).getProcessingPeriodId(),
        requisition.getProcessingPeriodId());
    assertEquals(
        receivedRequisitions.get(0).getSupervisoryNodeId(),
        requisition.getSupervisoryNodeId());
    assertEquals(
        receivedRequisitions.get(0).getStatus(),
        requisition.getStatus());
  }

  @Test
  public void shouldFindAndSortAndPageApprovedRequisitions() {
    // given
    List<RequisitionDto> requisitionDtos = getRequisitionDtoList();
    String filterAndSortBy = "programName";
    int pageNumber = 1;
    int pageSize = 5;

    setupStubsForTestApprovedRequisition(requisitionDtos, filterAndSortBy, null,
        pageNumber, pageSize, filterAndSortBy);

    requisitionDtos.sort(new RequisitionDtoComparator(filterAndSortBy));
    Collections.reverse(requisitionDtos);

    //when
    List<RequisitionDto> requisitionDtosRetrieved =
        requisitionService.searchApprovedRequisitionsWithSortAndFilterAndPaging(null,
            filterAndSortBy, filterAndSortBy, true, pageNumber, pageSize);

    //then
    verify(paginationHelper).pageCollection(requisitionDtos, pageNumber, pageSize);
    assertEquals(requisitionDtos, requisitionDtosRetrieved);
  }

  @Test
  public void shouldConvertRequisitionsToOrders() throws RequisitionException {
    // given
    int requisitionsCount = 5;

    UserDto user = mock(UserDto.class);
    when(user.getId()).thenReturn(UUID.randomUUID());

    List<ConvertToOrderDto> list = setUpReleaseRequisitionsAsOrder(requisitionsCount);

    List<FacilityDto> facilities = list.stream()
        .map(r -> facilityReferenceDataService.findOne(r.getSupplyingDepotId()))
        .collect(Collectors.toList());

    when(fulfillmentFacilitiesReferenceDataService.getFulfillmentFacilities(user.getId()))
        .thenReturn(facilities);
    when(orderFulfillmentService.create(any())).thenReturn(true);

    // when
    requisitionService.convertToOrder(list, user);

    // then
    verify(orderFulfillmentService, atLeastOnce()).create(any(OrderDto.class));
  }

  @Test(expected = RequisitionConversionException.class)
  public void shouldNotConvertRequisitionToOrderWhenCreatingOrderInFulfillmentServiceFailed()
      throws RequisitionException {
    // given
    int requisitionsCount = 5;

    UserDto user = mock(UserDto.class);
    when(user.getId()).thenReturn(UUID.randomUUID());

    List<ConvertToOrderDto> list = setUpReleaseRequisitionsAsOrder(requisitionsCount);

    List<FacilityDto> facilities = list.stream()
        .map(r -> facilityReferenceDataService.findOne(r.getSupplyingDepotId()))
        .collect(Collectors.toList());

    when(fulfillmentFacilitiesReferenceDataService.getFulfillmentFacilities(user.getId()))
        .thenReturn(facilities);
    when(orderFulfillmentService.create(any())).thenReturn(false);

    // when
    requisitionService.convertToOrder(list, user);
  }

  @Test
  public void shouldNotifyUserWhenConvertingRequisitionToOrder() throws Exception {
    UserDto user = mock(UserDto.class);
    when(user.getId()).thenReturn(UUID.randomUUID());

    List<ConvertToOrderDto> list = setUpReleaseRequisitionsAsOrder(1);

    List<FacilityDto> facilities = list.stream()
        .map(r -> facilityReferenceDataService.findOne(r.getSupplyingDepotId()))
        .collect(Collectors.toList());

    when(fulfillmentFacilitiesReferenceDataService.getFulfillmentFacilities(user.getId()))
        .thenReturn(facilities);
    when(orderFulfillmentService.create(any())).thenReturn(true);

    requisitionService.convertToOrder(list, user);

    verify(notificationService).notify(refEq(user), anyString(), anyString());
  }

  private List<ConvertToOrderDto> setUpReleaseRequisitionsAsOrder(int amount) {
    if (amount < 1) {
      throw new IllegalArgumentException("Amount must be a positive number");
    }

    List<ConvertToOrderDto> result = new ArrayList<>();

    for (int i = 0; i < amount; i++) {
      FacilityDto facility = mock(FacilityDto.class);
      when(facility.getId()).thenReturn(UUID.randomUUID());

      Requisition requisition = new Requisition(UUID.randomUUID(), UUID.randomUUID(),
          UUID.randomUUID(), APPROVED, false);
      requisition.setId(UUID.randomUUID());
      requisition.setSupervisoryNodeId(UUID.randomUUID());
      requisition.setSupplyingFacilityId(facility.getId());
      requisition.setRequisitionLineItems(Lists.newArrayList());

      when(requisitionRepository.findOne(requisition.getId())).thenReturn(requisition);
      when(facilityReferenceDataService.findOne(facility.getId())).thenReturn(facility);
      when(facilityReferenceDataService
          .searchSupplyingDepots(requisition.getProgramId(), requisition.getSupervisoryNodeId()))
          .thenReturn(Collections.singletonList(facility));

      result.add(new ConvertToOrderDto(requisition.getId(), facility.getId()));
    }

    return result;
  }

  private Requisition generateRequisition() {
    requisition = new Requisition(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        INITIATED, false);
    requisition.setId(UUID.randomUUID());
    requisition.setCreatedDate(LocalDateTime.now());
    requisition.setSupplyingFacilityId(facilityId);
    List<RequisitionLineItem> requisitionLineItems = new ArrayList<>();
    requisitionLineItems.add(mock(RequisitionLineItem.class));
    requisition.setRequisitionLineItems(requisitionLineItems);
    return requisition;
  }

  private SupplyLineDto generateSupplyLine(
      UUID program, UUID supervisoryNode, UUID facility) {
    SupplyLineDto supplyLine = new SupplyLineDto();
    supplyLine.setProgram(program);
    supplyLine.setSupervisoryNode(supervisoryNode);
    supplyLine.setSupplyingFacility(facility);
    return supplyLine;
  }

  private List<RequisitionDto> getRequisitionDtoList() {
    List<RequisitionDto> requisitionDtos = new ArrayList<>();
    String[] programNames = {"one", "two", "three", "four", "five"};

    for (String programName : programNames) {
      RequisitionDto requisitionDto = new RequisitionDto();
      ProgramDto programDto = new ProgramDto();
      programDto.setName(programName);
      requisitionDto.setProgram(programDto);
      requisitionDtos.add(requisitionDto);
    }
    return requisitionDtos;
  }

  private void setupStubsForTestApprovedRequisition(List<RequisitionDto> requisitionDtos,
                                                    String filterBy, String filterValue,
                                                    int pageNumber, int pageSize, String
                                                        programName) {
    List<UUID> desiredUuids = new ArrayList<>();
    List<Requisition> requisitions = new ArrayList<>();
    when(programReferenceDataService.search(programName))
        .thenReturn(Collections.emptyList());
    when(requisitionRepository.searchApprovedRequisitions(filterBy, desiredUuids))
        .thenReturn(requisitions);
    when(convertHelper.convertRequisitionListToRequisitionDtoList(requisitions))
        .thenReturn(requisitionDtos);
    when(paginationHelper.pageCollection(any(), eq(pageNumber), eq(pageSize)))
        .then(i -> i.getArgumentAt(0, List.class));
  }

  private void mockRepositories() throws RequisitionException {
    ProcessingScheduleDto processingScheduleDto = new ProcessingScheduleDto();
    processingScheduleDto.setId(UUID.randomUUID());

    when(requisitionRepository
        .findOne(requisition.getId()))
        .thenReturn(requisition);
    when(requisitionRepository
        .save(requisition))
        .thenReturn(requisition);
    when(requisitionRepository
        .save(requisition))
        .thenReturn(requisition);
    when(programReferenceDataService
        .findOne(any()))
        .thenReturn(program);

    processingPeriodDto = new ProcessingPeriodDto();
    processingPeriodDto.setProcessingSchedule(processingScheduleDto);
    processingPeriodDto.setId(suggestedPeriodId);
    when(periodService
        .getPeriod(any()))
        .thenReturn(processingPeriodDto);

    when(periodService
        .findPeriod(programId, facilityId, suggestedPeriodId, false))
        .thenReturn(processingPeriodDto);

    when(scheduleReferenceDataService.searchByProgramAndFacility(any(), any()))
        .thenReturn(Arrays.asList(processingScheduleDto));

    when(periodService.searchByProgramAndFacility(any(), any()))
        .thenReturn(Arrays.asList(processingPeriodDto));

    when(requisitionRepository.searchByProcessingPeriodAndType(any(), any()))
        .thenReturn(new ArrayList<>());

    when(periodReferenceDataService
        .findOne(any()))
        .thenReturn(processingPeriodDto);
  }
}
