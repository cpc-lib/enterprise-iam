package com.enterprise.iam.sharing.application.port.out;

import com.enterprise.iam.sharing.domain.model.ShareAcl;

import java.util.List;

public interface ShareAclRepository {

    List<ShareAcl> saveAll(List<ShareAcl> acls);

    List<ShareAcl> findByShareId(long tenantId, long shareId);
}
