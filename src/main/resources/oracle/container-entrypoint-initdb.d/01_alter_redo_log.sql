ALTER DATABASE ADD LOGFILE GROUP 3 ('/opt/oracle/oradata/FREE/redo03.log') SIZE 1024M;
ALTER DATABASE ADD LOGFILE GROUP 4 ('/opt/oracle/oradata/FREE/redo04.log') SIZE 1024M;

alter system switch logfile;

alter database drop logfile group 1;
alter database drop logfile group 2;