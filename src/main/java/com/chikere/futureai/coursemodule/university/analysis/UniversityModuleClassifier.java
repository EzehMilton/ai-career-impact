package com.chikere.futureai.coursemodule.university.analysis;

import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleClassification;
import com.chikere.futureai.coursemodule.university.policy.UniversityModulePolicyV1;

import java.util.List;

/** The deterministic classification a snapshot is made with; {@link UniversityModulePolicyV1} in production. */
public interface UniversityModuleClassifier {

    List<UniversityModuleClassification> classify(UniversityModuleJudgmentResult judgments);

    String policyVersion();

    String supportedSchemaVersion();

    static UniversityModuleClassifier v1(UniversityModulePolicyV1 policy) {
        return new UniversityModuleClassifier() {
            @Override
            public List<UniversityModuleClassification> classify(UniversityModuleJudgmentResult judgments) {
                return policy.classify(judgments);
            }

            @Override
            public String policyVersion() {
                return UniversityModulePolicyV1.POLICY_VERSION;
            }

            @Override
            public String supportedSchemaVersion() {
                return UniversityModulePolicyV1.SUPPORTED_SCHEMA_VERSION;
            }
        };
    }
}
