package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.MenuRepository;
import com.enterprise.iam.identity.application.port.out.RoleRepository;
import com.enterprise.iam.identity.application.port.out.UserRoleRepository;
import com.enterprise.iam.identity.domain.model.Menu;
import com.enterprise.iam.identity.domain.model.Role;
import com.enterprise.iam.identity.domain.model.UserRole;
import com.enterprise.iam.identity.domain.model.navigation.NavItem;
import com.enterprise.iam.identity.domain.model.navigation.NavSchemaNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GetNavigationSchemaUseCase {

    private final MenuRepository menuRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public GetNavigationSchemaUseCase() {
        this(null, null, null);
    }

    public GetNavigationSchemaUseCase(MenuRepository menuRepository) {
        this(menuRepository, null, null);
    }

    public GetNavigationSchemaUseCase(
            MenuRepository menuRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository) {
        this.menuRepository = menuRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    public List<NavSchemaNode> getSchema() {
        return getSchema(10001L, null);
    }

    public List<NavSchemaNode> getSchema(long tenantId) {
        return getSchema(tenantId, null);
    }

    public List<NavSchemaNode> getSchema(long tenantId, Long userId) {
        if (menuRepository == null) {
            return List.of();
        }

        List<Menu> menus;
        if (userId != null) {
            // 超级管理员无任何菜单限制，无条件拥有租户全量活动菜单
            if (isSuperAdmin(tenantId, userId)) {
                menus = menuRepository.findPublishedActive(tenantId);
            } else {
                // 纯数据库数据控制：根据用户角色和关联的角色-菜单表查询其授权的活动菜单
                menus = menuRepository.findPublishedActiveByUser(tenantId, userId);
                // 普通租户管理员若未细化配置角色菜单，则下发租户全量活动菜单
                if ((menus == null || menus.isEmpty()) && isAdminUser(tenantId, userId)) {
                    menus = menuRepository.findPublishedActive(tenantId);
                }
            }
        } else {
            menus = menuRepository.findPublishedActive(tenantId);
        }

        if (menus == null || menus.isEmpty()) {
            return List.of();
        }

        return buildNodesFromMenus(menus, tenantId);
    }

    private boolean isSuperAdmin(long tenantId, long userId) {
        if (tenantId == 10001L) {
            return true;
        }
        if (userRoleRepository == null || roleRepository == null) {
            return false;
        }
        List<UserRole> activeRoles = userRoleRepository.findActiveByUser(tenantId, userId);
        if (activeRoles == null || activeRoles.isEmpty()) {
            return false;
        }
        for (UserRole ur : activeRoles) {
            Optional<Role> roleOpt = roleRepository.findById(tenantId, ur.roleId());
            if (roleOpt.isPresent()) {
                String code = roleOpt.get().roleCode();
                if ("SUPER_ADMIN".equalsIgnoreCase(code)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAdminUser(long tenantId, long userId) {
        if (tenantId == 10001L) {
            return true;
        }
        if (userRoleRepository == null || roleRepository == null) {
            return false;
        }
        List<UserRole> activeRoles = userRoleRepository.findActiveByUser(tenantId, userId);
        if (activeRoles == null || activeRoles.isEmpty()) {
            return false;
        }
        for (UserRole ur : activeRoles) {
            Optional<Role> roleOpt = roleRepository.findById(tenantId, ur.roleId());
            if (roleOpt.isPresent()) {
                String code = roleOpt.get().roleCode();
                if ("SUPER_ADMIN".equalsIgnoreCase(code) || "TENANT_ADMIN".equalsIgnoreCase(code)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<NavSchemaNode> buildNodesFromMenus(List<Menu> menus, long tenantId) {
        // 自动补全子项所依赖的父级目录节点，杜绝因仅勾选子菜单导致目录在侧边栏丢失
        List<Menu> allPublished = menuRepository.findPublishedActive(tenantId);
        Map<Long, Menu> allMap = allPublished.stream()
                .collect(Collectors.toMap(Menu::id, m -> m, (a, b) -> a));

        Map<Long, Menu> effectiveMenus = new LinkedHashMap<>();
        for (Menu m : menus) {
            effectiveMenus.put(m.id(), m);
            Long pid = m.parentId();
            while (pid != null && !effectiveMenus.containsKey(pid)) {
                Menu parent = allMap.get(pid);
                if (parent != null) {
                    effectiveMenus.put(parent.id(), parent);
                    pid = parent.parentId();
                } else {
                    break;
                }
            }
        }

        Map<Long, List<Menu>> childrenByParent = new LinkedHashMap<>();
        List<Menu> rootMenus = new ArrayList<>();

        for (Menu m : effectiveMenus.values()) {
            if (m.parentId() == null) {
                rootMenus.add(m);
            } else {
                childrenByParent.computeIfAbsent(m.parentId(), k -> new ArrayList<>()).add(m);
            }
        }

        List<NavSchemaNode> nodes = new ArrayList<>();
        int orderIndex = 1;
        for (Menu root : rootMenus) {
            List<Menu> children = childrenByParent.get(root.id());
            List<NavItem> items = null;
            if (children != null && !children.isEmpty()) {
                items = children.stream()
                        .map(c -> new NavItem(c.menuKey(), c.path(), c.title(), c.icon(), c.sortOrder()))
                        .toList();
            }
            nodes.add(new NavSchemaNode(
                    root.menuKey(),
                    root.title(),
                    root.path(),
                    root.icon(),
                    root.sortOrder() > 0 ? root.sortOrder() : orderIndex++,
                    items));
        }
        return nodes;
    }
}
