-- Existing rows may remain NULL; application code requires the CNN snapshot for new RECOMMENDING transitions.
ALTER TABLE analysis_jobs ADD COLUMN cnn_result_json TEXT;
ALTER TABLE analysis_jobs ADD COLUMN llm_result_json TEXT;
ALTER TABLE analysis_jobs ADD COLUMN survey_result_json TEXT;
