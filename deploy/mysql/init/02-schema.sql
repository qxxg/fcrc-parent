-- FCRC 业务表结构（MySQL 8.x / utf8mb4）。
--
-- 说明：
-- 1. 六个业务服务共享一个 fcrc 库，但表按领域归属，不在数据库层做跨服务外键，
--    避免把微服务耦合进 DDL；跨域一致性由工作流与事件表保证。
-- 2. 应用侧仍保留 spring.jpa.hibernate.ddl-auto=update，便于本地/测试自动建表；
--    生产部署以本脚本为准，Hibernate 只做增量兼容。
-- 3. 时间统一存 UTC（应用侧 Instant），MySQL 会话时区在连接串中声明。

SET NAMES utf8mb4;
USE fcrc;

CREATE TABLE IF NOT EXISTS customers (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    customer_no       VARCHAR(32)  NOT NULL,
    name              VARCHAR(128) NOT NULL,
    customer_type     VARCHAR(16)  NOT NULL COMMENT 'INDIVIDUAL / CORPORATE',
    id_number         VARCHAR(64)  NULL,
    phone             VARCHAR(32)  NULL,
    email             VARCHAR(128) NULL,
    country           VARCHAR(64)  NOT NULL,
    industry          VARCHAR(64)  NULL,
    annual_income     DECIMAL(18, 2) NULL,
    occupation        VARCHAR(64)  NULL,
    pep               BIT(1)       NOT NULL DEFAULT b'0',
    channel           VARCHAR(16)  NULL,
    kyc_status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    onboarding_status VARCHAR(24)  NOT NULL DEFAULT 'NOT_STARTED',
    risk_level        VARCHAR(16)  NULL,
    notes             VARCHAR(1000) NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    version           BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customers_customer_no (customer_no),
    KEY idx_customers_name (name),
    KEY idx_customers_status (kyc_status, onboarding_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '客户主数据与 KYC/开户状态';

CREATE TABLE IF NOT EXISTS risk_assessments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    assessment_no   VARCHAR(40)   NOT NULL,
    customer_no     VARCHAR(32)   NOT NULL,
    score           INT           NOT NULL,
    level           VARCHAR(16)   NOT NULL,
    factors_json    LONGTEXT      NOT NULL,
    recommendation  VARCHAR(1000) NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_assessment_no (assessment_no),
    KEY idx_risk_customer_no (customer_no),
    KEY idx_risk_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '风险评分结果，factors_json 保留可解释因子';

CREATE TABLE IF NOT EXISTS compliance_screenings (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    screening_no    VARCHAR(40)   NOT NULL,
    customer_no     VARCHAR(32)   NOT NULL,
    result          VARCHAR(16)   NOT NULL,
    risk_points     INT           NOT NULL,
    findings_json   LONGTEXT      NOT NULL,
    recommendation  VARCHAR(1000) NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compliance_screening_no (screening_no),
    KEY idx_compliance_customer_no (customer_no),
    KEY idx_compliance_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'KYC/AML/制裁/负面舆情筛查结果';

CREATE TABLE IF NOT EXISTS compliance_cases (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    case_no       VARCHAR(40)   NOT NULL,
    customer_no   VARCHAR(32)   NOT NULL,
    customer_name VARCHAR(128)  NULL,
    type          VARCHAR(32)   NOT NULL,
    priority      VARCHAR(16)   NOT NULL,
    status        VARCHAR(24)   NOT NULL DEFAULT 'OPEN',
    title         VARCHAR(200)  NOT NULL,
    description   VARCHAR(2000) NULL,
    assignee      VARCHAR(64)   NULL,
    resolution    VARCHAR(2000) NULL,
    source_ref    VARCHAR(64)   NULL,
    due_at        DATETIME(6)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    version       BIGINT        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compliance_case_no (case_no),
    KEY idx_case_customer_no (customer_no),
    KEY idx_case_status_priority (status, priority),
    KEY idx_case_assignee (assignee)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '人工复核产生的合规案件';

CREATE TABLE IF NOT EXISTS case_notes (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    case_no    VARCHAR(40)   NOT NULL,
    author     VARCHAR(64)   NOT NULL,
    content    VARCHAR(2000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_case_note_case_no (case_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '案件备注，只追加不修改';

CREATE TABLE IF NOT EXISTS regulatory_reports (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    report_no          VARCHAR(40)   NOT NULL,
    customer_no        VARCHAR(32)   NOT NULL,
    workflow_no        VARCHAR(40)   NULL,
    report_type        VARCHAR(32)   NOT NULL,
    period             VARCHAR(16)   NULL,
    decision           VARCHAR(24)   NOT NULL,
    risk_score         INT           NULL,
    risk_level         VARCHAR(16)   NULL,
    compliance_result  VARCHAR(16)   NULL,
    content_json       LONGTEXT      NOT NULL,
    summary            VARCHAR(2000) NOT NULL,
    status             VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    created_at         DATETIME(6)   NOT NULL,
    finalized_at       DATETIME(6)   NULL,
    submitted_at       DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_regulatory_report_no (report_no),
    KEY idx_report_customer_no (customer_no),
    KEY idx_report_type_status (report_type, status),
    KEY idx_report_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '监管报告：客户风险报告/SAR/CTR/周期报告';

CREATE TABLE IF NOT EXISTS workflow_runs (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    run_no                   VARCHAR(40)   NOT NULL,
    thread_id                VARCHAR(40)   NOT NULL,
    customer_id              BIGINT        NOT NULL,
    customer_no              VARCHAR(32)   NULL,
    customer_name            VARCHAR(128)  NULL,
    status                   VARCHAR(24)   NOT NULL DEFAULT 'RUNNING',
    current_node             VARCHAR(64)   NULL,
    risk_assessment_no       VARCHAR(40)   NULL,
    risk_score               INT           NULL,
    risk_level               VARCHAR(16)   NULL,
    risk_factors_json        LONGTEXT      NULL,
    compliance_screening_no  VARCHAR(40)   NULL,
    compliance_result        VARCHAR(16)   NULL,
    compliance_findings_json LONGTEXT      NULL,
    preliminary_decision     VARCHAR(24)   NULL,
    decision                 VARCHAR(24)   NULL,
    decision_reason          VARCHAR(1000) NULL,
    review_decision          VARCHAR(24)   NULL,
    reviewer                 VARCHAR(64)   NULL,
    review_comment           VARCHAR(1000) NULL,
    case_no                  VARCHAR(40)   NULL,
    report_no                VARCHAR(40)   NULL,
    onboarding_status        VARCHAR(24)   NULL,
    audit_trail_json         LONGTEXT      NULL,
    error_message            VARCHAR(1000) NULL,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    version                  BIGINT        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_run_no (run_no),
    KEY idx_workflow_thread_id (thread_id),
    KEY idx_workflow_status (status),
    KEY idx_workflow_customer_id (customer_id),
    KEY idx_workflow_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '开户+风险+合规联合工作流聚合';

CREATE TABLE IF NOT EXISTS workflow_events (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    run_no     VARCHAR(40)  NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    node       VARCHAR(64)  NULL,
    message    VARCHAR(500) NOT NULL,
    payload    LONGTEXT     NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_wf_event_run (run_no, id),
    KEY idx_wf_event_type (type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流事件时间线，只追加';

CREATE TABLE IF NOT EXISTS workflow_checkpoints (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    checkpoint_id VARCHAR(64)  NOT NULL,
    thread_id     VARCHAR(64)  NOT NULL,
    node_id       VARCHAR(64)  NOT NULL,
    next_node_id  VARCHAR(64)  NOT NULL,
    state_data    LONGBLOB     NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_wf_checkpoint_thread (thread_id, id),
    KEY idx_wf_checkpoint_lookup (thread_id, checkpoint_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Graph 状态检查点，支持 HITL 暂停/恢复';
