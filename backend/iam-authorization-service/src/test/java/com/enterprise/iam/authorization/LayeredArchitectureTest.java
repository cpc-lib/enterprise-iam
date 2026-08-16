package com.enterprise.iam.authorization;

import com.enterprise.iam.testsupport.architecture.LayeredArchitectureRules;
import org.junit.jupiter.api.Test;

class LayeredArchitectureTest {

    @Test
    void respectsFrozenServiceLayers() {
        LayeredArchitectureRules.verify("com.enterprise.iam.authorization");
    }
}

