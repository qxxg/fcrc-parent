-- 教学演示数据（仅在首次初始化 MySQL 数据卷时执行）。
--
-- 设计取舍：
-- * 客户数据在这里显式插入，保证 dashboard 打开即有内容；
-- * 风险/合规/案件/报告仍由各服务启动时的 DemoDataConfiguration 生成，
--   因为它们的编号、评分和 JSON 结构由业务逻辑计算，避免在 SQL 中重复实现；
-- * 工作流只插入“已完成”历史记录，不插入 WAITING_REVIEW，
--   否则没有对应 checkpoint，人工复核会正确报“不可恢复”，反而造成误解。

SET NAMES utf8mb4;
USE fcrc;

INSERT IGNORE INTO customers
(id, customer_no, name, customer_type, id_number, phone, email, country, industry,
 annual_income, occupation, pep, channel, kyc_status, onboarding_status, risk_level, notes,
 created_at, updated_at, version)
VALUES
(1, 'CUS-DEMO-0001', '张伟', 'INDIVIDUAL', '110101199001011234', '13800001234',
 'zhangwei@example.com', 'China', '科技', 480000.00, '软件架构师', b'0', 'WEB',
 'APPROVED', 'COMPLETED', 'LOW', '演示数据，仅用于教学环境', NOW(6), NOW(6), 0),
(2, 'CUS-DEMO-0002', '李娜', 'INDIVIDUAL', '310101199205052345', '13900002345',
 'lina@example.com', 'China', '金融', 1200000.00, '投资经理', b'0', 'APP',
 'IN_REVIEW', 'MANUAL_REVIEW', 'MEDIUM', '演示数据，仅用于教学环境', NOW(6), NOW(6), 0),
(3, 'CUS-DEMO-0003', '王强', 'INDIVIDUAL', '440301198803033456', '13700003456',
 'wangqiang@example.com', 'Singapore', '国际贸易', 2500000.00, '企业主', b'1', 'BRANCH',
 'IN_REVIEW', 'MANUAL_REVIEW', 'HIGH', '演示数据，仅用于教学环境', NOW(6), NOW(6), 0),
(4, 'CUS-DEMO-0004', '陈静', 'INDIVIDUAL', '320101199507074567', '13600004567',
 'chenjing@example.com', 'Iran', '能源', 300000.00, '采购专员', b'0', 'WEB',
 'REJECTED', 'COMPLETED', 'CRITICAL', '演示数据，仅用于教学环境', NOW(6), NOW(6), 0),
(5, 'CUS-DEMO-0005', '华星科技有限公司', 'CORPORATE', '91310000MA1K12345X', '021-88886666',
 'compliance@huaxing.example.com', 'China', '跨境电商', 50000000.00, NULL, b'0', 'PARTNER',
 'APPROVED', 'COMPLETED', 'MEDIUM', '演示数据，仅用于教学环境', NOW(6), NOW(6), 0),
(6, 'CUS-DEMO-0006', '北辰资本控股', 'CORPORATE', 'KY-889922', '+852-9000-6677',
 'ops@beichen.example.com', 'Cayman Islands', '投资控股', 180000000.00, NULL, b'1', 'PARTNER',
 'PENDING', 'NOT_STARTED', 'HIGH', '演示数据，仅用于教学环境', NOW(6), NOW(6), 0);

INSERT IGNORE INTO workflow_runs
(id, run_no, thread_id, customer_id, customer_no, customer_name, status, current_node,
 risk_assessment_no, risk_score, risk_level, risk_factors_json,
 compliance_screening_no, compliance_result, compliance_findings_json,
 preliminary_decision, decision, decision_reason, review_decision, reviewer, review_comment,
 case_no, report_no, onboarding_status, audit_trail_json, error_message,
 created_at, updated_at, version)
VALUES
(1, 'WF-DEMO-0001', 'WF-DEMO-0001', 1, 'CUS-DEMO-0001', '张伟', 'COMPLETED', 'finalize_approval',
 'RSK-DEMO-0001', 8, 'LOW', '[{"code":"BASE","label":"基础风险","points":8,"description":"低风险画像"}]',
 'SCR-DEMO-0001', 'PASS', '[]',
 'AUTO_APPROVE', 'APPROVED', '合规 PASS、风险分 8 < 40、客户非 PEP，满足自动审批条件',
 NULL, NULL, NULL, NULL, 'RPT-DEMO-0001', 'COMPLETED',
 '["load_customer","risk_assess","compliance_screen","aggregate","auto_approve","finalize_approval"]',
 NULL, DATE_SUB(NOW(6), INTERVAL 2 DAY), DATE_SUB(NOW(6), INTERVAL 1 DAY), 0),
(2, 'WF-DEMO-0002', 'WF-DEMO-0002', 2, 'CUS-DEMO-0002', '李娜', 'REJECTED', 'finalize_rejection',
 'RSK-DEMO-0002', 38, 'MEDIUM', '[{"code":"CROSS_BORDER","label":"跨境交易","points":15,"description":"跨境资金流动"}]',
 'SCR-DEMO-0002', 'REVIEW', '[{"code":"SOURCE_OF_FUNDS","label":"资金来源待补充","points":20,"description":"需人工核实"}]',
 'MANUAL_REVIEW', 'REJECTED', '人工复核后拒绝：资金来源材料不足',
 'REJECTED', 'compliance.li', '补充材料仍无法解释大额资金来源',
 'CASE-DEMO-0001', 'RPT-DEMO-0002', 'REJECTED',
 '["load_customer","risk_assess","compliance_screen","aggregate","manual_review","finalize_rejection"]',
 NULL, DATE_SUB(NOW(6), INTERVAL 3 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY), 0);

INSERT IGNORE INTO workflow_events (run_no, type, node, message, payload, created_at)
VALUES
('WF-DEMO-0001', 'WORKFLOW_STARTED', 'START', '工作流已启动', NULL, DATE_SUB(NOW(6), INTERVAL 2 DAY)),
('WF-DEMO-0001', 'NODE_COMPLETED', 'risk_assess', '风险评分完成：8 / LOW', NULL, DATE_SUB(NOW(6), INTERVAL 2 DAY)),
('WF-DEMO-0001', 'NODE_COMPLETED', 'compliance_screen', '合规筛查完成：PASS', NULL, DATE_SUB(NOW(6), INTERVAL 2 DAY)),
('WF-DEMO-0001', 'BRANCH_DECISION', 'aggregate', '满足自动审批条件', NULL, DATE_SUB(NOW(6), INTERVAL 2 DAY)),
('WF-DEMO-0001', 'WORKFLOW_COMPLETED', 'finalize_approval', '工作流完成：APPROVED', NULL, DATE_SUB(NOW(6), INTERVAL 1 DAY)),
('WF-DEMO-0002', 'WORKFLOW_STARTED', 'START', '工作流已启动', NULL, DATE_SUB(NOW(6), INTERVAL 3 DAY)),
('WF-DEMO-0002', 'BRANCH_DECISION', 'aggregate', '转人工复核', NULL, DATE_SUB(NOW(6), INTERVAL 3 DAY)),
('WF-DEMO-0002', 'HUMAN_REVIEW', 'manual_review', '人工复核拒绝：compliance.li', NULL, DATE_SUB(NOW(6), INTERVAL 2 DAY)),
('WF-DEMO-0002', 'WORKFLOW_COMPLETED', 'finalize_rejection', '工作流完成：REJECTED', NULL, DATE_SUB(NOW(6), INTERVAL 2 DAY));
