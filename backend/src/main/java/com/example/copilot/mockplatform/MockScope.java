package com.example.copilot.mockplatform;

/** Which customers a mock account may see; decided by the account, not by the role code. */
public enum MockScope {
    ALL, REGION, BRANCH, BRANCH_WEALTH, ASSIGNEE_WEALTH, MARKETING_UNIT, ASSIGNEE_MARKETING
}
