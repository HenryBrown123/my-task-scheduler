package com.github.henrybrown123.shared.model.job.schedule;

enum TimeUnitShort {
    MINUTE("m"), HOUR("h"), DAY("d"),
    WEEK("w"), MONTH("M"), YEAR("y");

    private final String timeCode;

    TimeUnitShort(String timeCode) {
        this.timeCode = timeCode;
    }

    public String timeCode() {
        return timeCode;
    }

    public String description() {
        return timeCode + " - " + this.name();
    }
}
