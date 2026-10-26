package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.MenuRepository;
import com.enterprise.iam.identity.domain.model.Menu;
import com.enterprise.iam.identity.domain.model.MenuNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ListMenusUseCase {

    private final MenuRepository menuRepository;

    public ListMenusUseCase(MenuRepository menuRepository) {
        this.menuRepository = Objects.requireNonNull(menuRepository);
    }

    public List<MenuNode> listMenuTree(long tenantId) {
        List<Menu> menus = menuRepository.findAll(tenantId);
        Map<Long, MenuNode> map = new LinkedHashMap<>();

        for (Menu m : menus) {
            MenuNode node = new MenuNode(
                    m.id(),
                    m.tenantId(),
                    m.parentId(),
                    m.menuKey(),
                    m.title(),
                    m.path(),
                    m.icon(),
                    m.sortOrder(),
                    m.status(),
                    m.publishStatus(),
                    m.permissionCode(),
                    m.component(),
                    m.visible(),
                    m.version(),
                    m.createdAt(),
                    m.updatedAt());
            map.put(m.id(), node);
        }

        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : map.values()) {
            if (node.parentId() == null || !map.containsKey(node.parentId())) {
                roots.add(node);
            } else {
                MenuNode parent = map.get(node.parentId());
                parent.addChild(node);
            }
        }
        return roots;
    }

    public List<Menu> listAll(long tenantId) {
        return menuRepository.findAll(tenantId);
    }
}
