package vn.giapha.imports;

import java.util.List;

public record ImportReport(boolean dryRun, int persons, int unions, int links, List<ImportModel.Error> errors,
                           List<ImportModel.Error> warnings, Long clanId) {}
