ALTER DATABASE ADD LOGFILE GROUP 3 ('/opt/oracle/oradata/FREE/redo03.log') SIZE 1024M;
ALTER DATABASE ADD LOGFILE GROUP 4 ('/opt/oracle/oradata/FREE/redo04.log') SIZE 1024M;

alter system switch logfile;

alter database drop logfile group 1;
alter database drop logfile group 2;

ALTER SESSION SET CONTAINER=FREEPDB1;

GRANT SELECT ON v_$session TO "TEST";
GRANT SELECT ON v_$sql_plan_statistics_all TO "TEST";
GRANT SELECT ON v_$sql_plan TO "TEST";
GRANT SELECT ON v_$sql TO "TEST";
GRANT SELECT on V_$SESSION to "TEST";