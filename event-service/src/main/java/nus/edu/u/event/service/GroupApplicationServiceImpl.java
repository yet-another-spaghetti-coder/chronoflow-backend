package nus.edu.u.event.service;

import static nus.edu.u.common.enums.ErrorCodeConstants.ADD_MEMBERS_FAILED;
import static nus.edu.u.common.enums.ErrorCodeConstants.EVENT_NOT_FOUND;
import static nus.edu.u.common.enums.ErrorCodeConstants.GET_GROUP_ID_FAILED;
import static nus.edu.u.common.enums.ErrorCodeConstants.GROUP_MEMBER_ALREADY_EXISTS;
import static nus.edu.u.common.enums.ErrorCodeConstants.GROUP_NAME_EXISTS;
import static nus.edu.u.common.enums.ErrorCodeConstants.GROUP_NOT_FOUND;
import static nus.edu.u.common.enums.ErrorCodeConstants.USER_ALREADY_IN_OTHER_GROUP_OF_EVENT;
import static nus.edu.u.common.enums.ErrorCodeConstants.USER_NOT_FOUND;
import static nus.edu.u.common.enums.ErrorCodeConstants.USER_STATUS_INVALID;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.enums.CommonStatusEnum;
import nus.edu.u.framework.security.audit.AuditType;
import nus.edu.u.framework.security.audit.Auditable;
import nus.edu.u.event.convert.UserConvert;
import nus.edu.u.event.domain.dataobject.event.EventDO;
import nus.edu.u.event.domain.dataobject.group.DeptDO;
import nus.edu.u.event.domain.dataobject.user.UserGroupDO;
import nus.edu.u.event.domain.dto.group.CreateGroupReqVO;
import nus.edu.u.event.domain.dto.group.GroupRespVO;
import nus.edu.u.event.domain.dto.group.UpdateGroupReqVO;
import nus.edu.u.event.domain.dto.user.UserProfileRespVO;
import nus.edu.u.event.mapper.DeptMapper;
import nus.edu.u.event.mapper.EventMapper;
import nus.edu.u.event.mapper.UserGroupMapper;
import nus.edu.u.shared.rpc.group.GroupDTO;
import nus.edu.u.shared.rpc.group.GroupMemberDTO;
import nus.edu.u.shared.rpc.user.RoleBriefDTO;
import nus.edu.u.shared.rpc.user.UserInfoDTO;
import nus.edu.u.shared.rpc.user.UserProfileDTO;
import nus.edu.u.shared.rpc.user.UserRpcService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupApplicationServiceImpl implements GroupApplicationService {

    @DubboReference private UserRpcService userRpcService;
    private final DeptMapper deptMapper;
    private final EventMapper eventMapper;
    private final UserGroupMapper userGroupMapper;
    private final GroupMemberRemovalService groupMemberRemovalService;
    private final UserConvert userConvert;

    @Override
    @Transactional
    @Auditable(operation = "Create Group", type = AuditType.DATA_CHANGE,
               targetType = "Group", targetId = "#reqVO.eventId")
    public Long createGroup(CreateGroupReqVO reqVO) {
        log.info("Creating group: {}", reqVO.getName());
        EventDO event = eventMapper.selectById(reqVO.getEventId());
        if (ObjectUtil.isNull(event)) {
            throw exception(EVENT_NOT_FOUND);
        }

        boolean exists =
                deptMapper.selectCount(
                                new LambdaQueryWrapper<DeptDO>()
                                        .eq(DeptDO::getEventId, reqVO.getEventId())
                                        .eq(DeptDO::getName, reqVO.getName())
                                        .eq(DeptDO::getDeleted, false)
                                        .eq(DeptDO::getStatus, CommonStatusEnum.ENABLE.getStatus()))
                        > 0;
        if (exists) {
            throw exception(GROUP_NAME_EXISTS);
        }

        if (reqVO.getLeadUserId() != null) {
            ensureUserExists(reqVO.getLeadUserId());
        }

        DeptDO dept =
                DeptDO.builder()
                        .name(reqVO.getName())
                        .sort(reqVO.getSort())
                        .leadUserId(reqVO.getLeadUserId())
                        .eventId(reqVO.getEventId())
                        .remark(reqVO.getRemark())
                        .status(CommonStatusEnum.ENABLE.getStatus())
                        .build();
        // dept.setTenantId(event.getTenantId());

        deptMapper.insert(dept);
        if (dept.getId() == null) {
            throw exception(GET_GROUP_ID_FAILED);
        }

        if (reqVO.getLeadUserId() != null) {
            addMemberToGroup(dept.getId(), reqVO.getLeadUserId());
        }
        return dept.getId();
    }

    @Override
    @Transactional
    @Auditable(operation = "Update Group", type = AuditType.DATA_CHANGE,
               targetType = "Group", targetId = "#reqVO.id")
    public void updateGroup(UpdateGroupReqVO reqVO) {
        DeptDO existing = deptMapper.selectById(reqVO.getId());
        if (existing == null) {
            throw exception(GROUP_NOT_FOUND);
        }

        if (reqVO.getLeadUserId() != null) {
            ensureUserExists(reqVO.getLeadUserId());
        }

        DeptDO updateDept = new DeptDO();
        BeanUtil.copyProperties(reqVO, updateDept);

        deptMapper.updateById(updateDept);
    }

    @Override
    @Transactional
    public void deleteGroup(Long id) {
        DeptDO existing = deptMapper.selectById(id);
        if (existing == null) {
            throw exception(GROUP_NOT_FOUND);
        }

        List<GroupRespVO.MemberInfo> members = getGroupMembers(id);
        if (!members.isEmpty()) {
            List<Long> normalMemberIds =
                    members.stream()
                            .map(GroupRespVO.MemberInfo::getUserId)
                            .filter(memberId -> !Objects.equals(memberId, existing.getLeadUserId()))
                            .toList();
            if (!normalMemberIds.isEmpty()) {
                GroupApplicationService proxy = (GroupApplicationService) AopContext.currentProxy();
                proxy.removeMembersFromGroup(id, normalMemberIds);
            }
        }
        // remove any remaining relations (including the lead) to avoid blocking future assignments
        userGroupMapper.delete(
                new LambdaQueryWrapper<UserGroupDO>().eq(UserGroupDO::getDeptId, id));

        UpdateWrapper<DeptDO> updateWrapper = new UpdateWrapper<>();
        updateWrapper
                .set("status", CommonStatusEnum.DISABLE.getStatus())
                .set("deleted", true)
                .eq("id", id);
        deptMapper.update(null, updateWrapper);
    }

    @Override
    @Transactional
    public void addMemberToGroup(Long groupId, Long userId) {
        DeptDO group = ensureGroupExists(groupId);
        UserInfoDTO user = ensureUserExists(userId);
        if (!CommonStatusEnum.isEnable(user.getStatus())) {
            throw exception(USER_STATUS_INVALID);
        }

        UserGroupDO existingRelation =
                userGroupMapper.selectOne(
                        new LambdaQueryWrapper<UserGroupDO>()
                                .eq(UserGroupDO::getUserId, userId)
                                .eq(UserGroupDO::getEventId, group.getEventId()));
        if (existingRelation != null) {
            if (!existingRelation.getDeptId().equals(groupId)) {
                throw exception(USER_ALREADY_IN_OTHER_GROUP_OF_EVENT);
            }
            throw exception(GROUP_MEMBER_ALREADY_EXISTS);
        }

        UserGroupDO relation =
                UserGroupDO.builder()
                        .userId(userId)
                        .deptId(groupId)
                        .eventId(group.getEventId())
                        .joinTime(LocalDateTime.now())
                        .build();
        userGroupMapper.insert(relation);
    }

    @Override
    public void removeMemberFromGroup(Long groupId, Long userId) {
        groupMemberRemovalService.removeMemberFromGroup(groupId, userId);
    }

    @Override
    public List<GroupRespVO.MemberInfo> getGroupMembers(Long groupId) {
        List<UserGroupDO> relations =
                userGroupMapper.selectList(
                        new LambdaQueryWrapper<UserGroupDO>().eq(UserGroupDO::getDeptId, groupId));
        if (ObjectUtil.isEmpty(relations)) {
            return Collections.emptyList();
        }

        Set<Long> userIds =
                relations.stream()
                        .map(UserGroupDO::getUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(HashSet::new));
        Map<Long, UserInfoDTO> users = fetchUsers(userIds);

        return relations.stream()
                .map(
                        relation -> {
                            UserInfoDTO user = users.get(relation.getUserId());
                            if (user == null || !CommonStatusEnum.isEnable(user.getStatus())) {
                                return null;
                            }
                            var role = pickPrimaryRole(user.getRoles());
                            return GroupRespVO.MemberInfo.builder()
                                    .userId(user.getId())
                                    .username(user.getUsername())
                                    .email(user.getEmail())
                                    .phone(user.getPhone())
                                    .roleId(role != null ? role.getId() : null)
                                    .roleName(role != null ? role.getName() : null)
                                    .joinTime(relation.getJoinTime())
                                    .build();
                        })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Auditable(operation = "Add Members to Group", type = AuditType.DATA_CHANGE,
               targetType = "Group", targetId = "#groupId")
    public void addMembersToGroup(Long groupId, List<Long> userIds) {
        if (ObjectUtil.isEmpty(userIds)) {
            return;
        }

        List<Long> failed = new ArrayList<>();
        for (Long userId : userIds) {
            try {
                addMemberToGroup(groupId, userId);
            } catch (Exception ex) {
                log.error(
                        "Failed to add user {} to group {}: {}",
                        userId,
                        groupId,
                        ex.getMessage(),
                        ex);
                failed.add(userId);
            }
        }

        if (!failed.isEmpty()) {
            throw exception(ADD_MEMBERS_FAILED);
        }
    }

    @Override
    @Transactional
    public void removeMembersFromGroup(Long groupId, List<Long> userIds) {
        if (ObjectUtil.isEmpty(userIds)) {
            return;
        }

        Exception firstException = null;
        List<Long> failed = new ArrayList<>();

        for (Long userId : userIds) {
            try {
                groupMemberRemovalService.removeMemberFromGroup(groupId, userId);
            } catch (Exception ex) {
                if (firstException == null) {
                    firstException = ex;
                }
                failed.add(userId);
                log.error(
                        "Failed to remove user {} from group {}: {}",
                        userId,
                        groupId,
                        ex.getMessage(),
                        ex);
            }
        }

        if (firstException != null) {
            throw firstException instanceof RuntimeException
                    ? (RuntimeException) firstException
                    : new RuntimeException(firstException);
        }
    }

    @Override
    public List<GroupRespVO> getGroupsByEvent(Long eventId) {
        EventDO event = eventMapper.selectById(eventId);
        if (event == null) {
            throw exception(EVENT_NOT_FOUND);
        }
        List<DeptDO> groups =
                deptMapper.selectList(
                        new LambdaQueryWrapper<DeptDO>()
                                .eq(DeptDO::getEventId, eventId)
                                .eq(DeptDO::getDeleted, false)
                                .orderByAsc(DeptDO::getSort)
                                .orderByDesc(DeptDO::getCreateTime));
        if (CollectionUtils.isEmpty(groups)) {
            return Collections.emptyList();
        }

        return buildGroupResponses(groups, Collections.singletonMap(eventId, event));
    }

    @Override
    public Map<Long, List<GroupRespVO>> getGroupsByEventIds(Collection<Long> eventIds) {
        if (CollectionUtils.isEmpty(eventIds)) {
            return Collections.emptyMap();
        }

        List<DeptDO> groups =
                deptMapper.selectList(
                        new LambdaQueryWrapper<DeptDO>()
                                .in(DeptDO::getEventId, eventIds)
                                .eq(DeptDO::getDeleted, false));

        if (CollectionUtils.isEmpty(groups)) {
            return Collections.emptyMap();
        }

        Map<Long, EventDO> events = fetchEventsByIds(eventIds);
        Map<Long, List<GroupRespVO>> result = new HashMap<>();
        Map<Long, List<GroupRespVO>> grouped =
                buildGroupResponses(groups, events).stream()
                        .collect(Collectors.groupingBy(GroupRespVO::getEventId));
        for (Long eventId : eventIds) {
            result.put(eventId, grouped.getOrDefault(eventId, Collections.emptyList()));
        }
        return result;
    }

    @Override
    public Map<Long, List<GroupDTO>> getGroupDTOsByEventIds(Collection<Long> eventIds) {
        if (CollectionUtils.isEmpty(eventIds)) {
            return Collections.emptyMap();
        }

        List<Long> distinctEventIds =
                eventIds.stream().filter(Objects::nonNull).distinct().toList();

        List<DeptDO> groups =
                distinctEventIds.isEmpty()
                        ? List.of()
                        : deptMapper.selectList(
                                new LambdaQueryWrapper<DeptDO>()
                                        .in(DeptDO::getEventId, distinctEventIds)
                                        .eq(DeptDO::getDeleted, false));

        Map<Long, List<DeptDO>> groupsByEvent =
                groups.stream().collect(Collectors.groupingBy(DeptDO::getEventId));

        Set<Long> groupIds =
                groups.stream()
                        .map(DeptDO::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        List<UserGroupDO> relations =
                groupIds.isEmpty()
                        ? List.of()
                        : userGroupMapper.selectList(
                                new LambdaQueryWrapper<UserGroupDO>()
                                        .in(UserGroupDO::getDeptId, groupIds));

        Set<Long> memberIds =
                relations.stream()
                        .map(UserGroupDO::getUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<Long, UserInfoDTO> users = fetchUsers(memberIds);

        Map<Long, List<GroupMemberDTO>> membersByGroup = new HashMap<>();
        for (UserGroupDO relation : relations) {
            UserInfoDTO user = users.get(relation.getUserId());
            if (user == null || !CommonStatusEnum.isEnable(user.getStatus())) {
                continue;
            }
            GroupMemberDTO member =
                    GroupMemberDTO.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .build();
            membersByGroup
                    .computeIfAbsent(relation.getDeptId(), key -> new ArrayList<>())
                    .add(member);
        }

        Map<Long, List<GroupDTO>> result = new HashMap<>();
        for (Long eventId : eventIds) {
            if (eventId == null) {
                continue;
            }
            List<DeptDO> eventGroups = groupsByEvent.getOrDefault(eventId, List.of());
            if (eventGroups.isEmpty()) {
                result.put(eventId, List.of());
                continue;
            }
            List<GroupDTO> dtos =
                    eventGroups.stream()
                            .map(
                                    group -> {
                                        List<GroupMemberDTO> members =
                                                membersByGroup.getOrDefault(
                                                        group.getId(), Collections.emptyList());
                                        return GroupDTO.builder()
                                                .eventId(group.getEventId())
                                                .id(group.getId())
                                                .name(group.getName())
                                                .sort(group.getSort())
                                                .leadUserId(group.getLeadUserId())
                                                .remark(group.getRemark())
                                                .status(group.getStatus())
                                                .members(
                                                        members.isEmpty()
                                                                ? List.of()
                                                                : List.copyOf(members))
                                                .build();
                                    })
                            .toList();
            result.put(eventId, dtos);
        }

        return result;
    }

    @Override
    public List<UserProfileRespVO> getAllUserProfiles() {
        List<UserProfileDTO> dtos = userRpcService.getEnabledUserProfiles();
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }

        List<UserProfileRespVO> profiles = new ArrayList<>(dtos.size());
        for (Object dtoObj : dtos) {
            UserProfileRespVO profile = convertProfile(dtoObj);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        return profiles;
    }

    private UserProfileRespVO convertProfile(Object source) {
        if (source instanceof UserProfileDTO dto) {
            return userConvert.toProfile(dto);
        }
        if (source instanceof Map<?, ?> map) {
            UserProfileDTO dto = BeanUtil.toBean(map, UserProfileDTO.class);
            return userConvert.toProfile(dto);
        }
        log.warn(
                "Received unexpected user profile payload type: {}",
                source == null ? "null" : source.getClass());
        return null;
    }

    private RoleBriefDTO pickPrimaryRole(List<RoleBriefDTO> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        return roles.get(0);
    }

    private UserInfoDTO ensureUserExists(Long userId) {
        Map<Long, UserInfoDTO> users = fetchUsers(Collections.singleton(userId));
        UserInfoDTO user = users.get(userId);
        if (user == null) {
            throw exception(USER_NOT_FOUND);
        }
        return user;
    }

    private Map<Long, UserInfoDTO> fetchUsers(Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        Map<Long, UserInfoDTO> users = userRpcService.getUsers(userIds);
        return users == null ? Collections.emptyMap() : users;
    }

    private DeptDO ensureGroupExists(Long groupId) {
        DeptDO group = deptMapper.selectById(groupId);
        if (group == null) {
            throw exception(GROUP_NOT_FOUND);
        }
        return group;
    }

    private Map<Long, EventDO> fetchEventsByIds(Collection<Long> eventIds) {
        List<EventDO> events = eventMapper.selectBatchIds(eventIds);
        if (CollectionUtils.isEmpty(events)) {
            return Collections.emptyMap();
        }
        return events.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(EventDO::getId, event -> event));
    }

    private List<GroupRespVO> buildGroupResponses(
            List<DeptDO> groups, Map<Long, EventDO> eventsById) {
        if (CollectionUtils.isEmpty(groups)) {
            return Collections.emptyList();
        }

        Set<Long> leadUserIds =
                groups.stream()
                        .map(DeptDO::getLeadUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<Long, UserInfoDTO> leaders = fetchUsers(leadUserIds);

        Set<Long> groupIds = collectIds(groups);
        Map<Long, Integer> memberCounts =
                groupIds.isEmpty()
                        ? Collections.emptyMap()
                        : userGroupMapper
                                .selectList(
                                        new LambdaQueryWrapper<UserGroupDO>()
                                                .in(UserGroupDO::getDeptId, groupIds))
                                .stream()
                                .collect(
                                        Collectors.groupingBy(
                                                UserGroupDO::getDeptId,
                                                Collectors.summingInt(item -> 1)));
        Map<Long, List<GroupRespVO.MemberInfo>> membersByGroup = fetchMembersByGroupIds(groupIds);

        return groups.stream()
                .map(
                        group -> {
                            EventDO event = eventsById.get(group.getEventId());
                            UserInfoDTO leader =
                                    group.getLeadUserId() == null
                                            ? null
                                            : leaders.get(group.getLeadUserId());
                            String leadUserName = leader != null ? leader.getUsername() : null;
                            return GroupRespVO.builder()
                                    .id(group.getId())
                                    .name(group.getName())
                                    .sort(group.getSort())
                                    .leadUserId(group.getLeadUserId())
                                    .leadUserName(leadUserName)
                                    .remark(group.getRemark())
                                    .status(group.getStatus())
                                    .statusName(
                                            Objects.equals(
                                                            group.getStatus(),
                                                            CommonStatusEnum.ENABLE.getStatus())
                                                    ? CommonStatusEnum.ENABLE.getName()
                                                    : CommonStatusEnum.DISABLE.getName())
                                    .eventId(group.getEventId())
                                    .eventName(event != null ? event.getName() : null)
                                    .memberCount(memberCounts.getOrDefault(group.getId(), 0))
                                    .members(
                                            membersByGroup.getOrDefault(
                                                    group.getId(), Collections.emptyList()))
                                    .createTime(group.getCreateTime())
                                    .updateTime(group.getUpdateTime())
                                    .build();
                        })
                .toList();
    }

    private Set<Long> collectIds(List<DeptDO> groups) {
        return groups.stream()
                .map(DeptDO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<Long, List<GroupRespVO.MemberInfo>> fetchMembersByGroupIds(
            Collection<Long> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyMap();
        }
        List<UserGroupDO> relations =
                userGroupMapper.selectList(
                        new LambdaQueryWrapper<UserGroupDO>().in(UserGroupDO::getDeptId, groupIds));
        if (CollectionUtils.isEmpty(relations)) {
            return Collections.emptyMap();
        }

        Set<Long> userIds =
                relations.stream().map(UserGroupDO::getUserId).collect(Collectors.toSet());
        Map<Long, UserInfoDTO> users = fetchUsers(userIds);

        Map<Long, List<GroupRespVO.MemberInfo>> result = new HashMap<>();
        for (UserGroupDO relation : relations) {
            UserInfoDTO user = users.get(relation.getUserId());
            if (user == null || !CommonStatusEnum.isEnable(user.getStatus())) {
                continue;
            }
            var role = pickPrimaryRole(user.getRoles());
            GroupRespVO.MemberInfo member =
                    GroupRespVO.MemberInfo.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .phone(user.getPhone())
                            .roleId(role != null ? role.getId() : null)
                            .roleName(role != null ? role.getName() : null)
                            .joinTime(relation.getJoinTime())
                            .build();
            result.computeIfAbsent(relation.getDeptId(), key -> new ArrayList<>()).add(member);
        }
        return result;
    }
}
