package com.github.bandwidthlimiter.bucket.grid;

import com.github.bandwidthlimiter.bucket.AbstractBucket;
import com.github.bandwidthlimiter.bucket.BandwidthAlgorithms;
import com.github.bandwidthlimiter.bucket.BucketConfiguration;
import com.github.bandwidthlimiter.bucket.BucketState;

public class GridBucket extends AbstractBucket {

    private final GridProxy gridProxy;

    public GridBucket(BucketConfiguration configuration, GridProxy gridProxy) {
        super(configuration);
        this.gridProxy = gridProxy;
        GridBucketState initialState = new GridBucketState(configuration, BandwidthAlgorithms.createInitialState(configuration));
        gridProxy.setInitialState(initialState);
    }

    @Override
    protected long consumeAsMuchAsPossibleImpl(long limit) {
        return gridProxy.execute(new ConsumeAsMuchAsPossibleCommand(limit));
    }

    @Override
    protected boolean tryConsumeImpl(long tokensToConsume) {
        return gridProxy.execute(new TryConsumeCommand(tokensToConsume));
    }

    @Override
    protected boolean consumeOrAwaitImpl(long tokensToConsume, long waitIfBusyTimeLimit) throws InterruptedException {
        final boolean isWaitingLimited = waitIfBusyTimeLimit > 0;
        final ConsumeOrCalculateTimeToCloseDeficitCommand consumeCommand = new ConsumeOrCalculateTimeToCloseDeficitCommand(tokensToConsume);
        final long methodStartTime = isWaitingLimited? configuration.getTimeMeter().currentTime(): 0;

        while (true) {
            long timeToCloseDeficit = gridProxy.execute(consumeCommand);
            if (timeToCloseDeficit == 0) {
                return true;
            }
            if (timeToCloseDeficit == Long.MAX_VALUE) {
                return false;
            }

            if (isWaitingLimited) {
                long currentTime = configuration.getTimeMeter().currentTime();
                long methodDuration = currentTime - methodStartTime;
                if (methodDuration >= waitIfBusyTimeLimit) {
                    return false;
                }
                long sleepingTimeLimit = waitIfBusyTimeLimit - methodDuration;
                if (timeToCloseDeficit >= sleepingTimeLimit) {
                    return false;
                }
            }
            configuration.getTimeMeter().sleep(timeToCloseDeficit);
        }
    }

    @Override
    public BucketState createSnapshot() {
        long[] snapshotBytes = gridProxy.execute(new CreateSnapshotCommand());
        return new BucketState(snapshotBytes);
    }

}
