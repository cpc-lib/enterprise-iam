package com.enterprise.iam.identity.application.port.out;

import com.enterprise.iam.identity.domain.model.Menu;
import com.enterprise.iam.identity.domain.model.MenuPublishStatus;
import com.enterprise.iam.identity.domain.model.MenuStatus;

import java.util.List;
import java.util.Optional;

public interface MenuRepository {

    Menu save(Menu menu);

    Optional<Menu> findById(long tenantId, long menuId);

    Optional<Menu> findByKey(long tenantId, String menuKey);

    List<Menu> findAll(long tenantId);

    List<Menu> findPublishedActive(long tenantId);

    List<Menu> findPublishedActiveByUser(long tenantId, long userId);

    boolean update(Menu menu, long expectedVersion);

    boolean updateStatus(long tenantId, long menuId, MenuStatus status, long expectedVersion);

    boolean updatePublishStatus(long tenantId, long menuId, MenuPublishStatus publishStatus, long expectedVersion);

    int publishAllDrafts(long tenantId);

    boolean updateSortOrder(long tenantId, long menuId, int sortOrder);

    boolean delete(long tenantId, long menuId, long expectedVersion);
}
