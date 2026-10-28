/* Superseded: runtime table permissions are now applied by script 03.
   Do not reuse old failed SSMS sessions or request schema-wide delegation. */
THROW 51014, 'Use 03_grant_foundation_runtime.sql in a NEW administrator query after Flyway V1. No changes were made.', 1;
