FROM eclipse-temurin:25-jre

COPY lib/ /opt/perf/lib/

# mount /work to keep logbooks and logs
RUN mkdir -p /work/logbooks
WORKDIR /work

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp '/opt/perf/lib/*' org.performance.start.Main \"$@\"", "--"]
