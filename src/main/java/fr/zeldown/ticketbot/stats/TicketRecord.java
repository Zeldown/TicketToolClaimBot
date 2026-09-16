package fr.zeldown.ticketbot.stats;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class TicketRecord {

	private final long channel;

	private int    adds;
	private long   open;
	private long   staff;
	private long   claim;
	private long   owner;
	private long   close;
	private int    claims;
	private String type;
	private String team;
	private long   delete;
	private int    removes;
	private int    transfers;

	public long waitMs() {
		return this.claim == 0L || this.open == 0L ? 0L : this.claim - this.open;
	}

	public long handleMs() {
		final long end = this.close == 0L ? this.delete : this.close;
		return end == 0L || this.claim == 0L ? 0L : end - this.claim;
	}

	public boolean isOpen() {
		return this.close == 0L && this.delete == 0L;
	}

	public void apply(final @NonNull StatsEvent event) {
		this.type = event.getType();
		if (StatsEvent.OPEN.equals(event.getEvent())) {
			this.open = event.getTime();
			this.owner = event.getOwner();
		} else if (StatsEvent.CLAIM.equals(event.getEvent())) {
			this.claim = this.claim == 0L ? event.getTime() : this.claim;
			this.staff = event.getStaff();
			this.claims++;
		} else if (StatsEvent.TRANSFER.equals(event.getEvent())) {
			this.team = event.getTeam();
			this.transfers++;
		} else if (StatsEvent.ADD.equals(event.getEvent())) {
			this.adds++;
		} else if (StatsEvent.REMOVE.equals(event.getEvent())) {
			this.removes++;
		} else if (StatsEvent.CLOSE.equals(event.getEvent())) {
			this.close = event.getTime();
		} else if (StatsEvent.DELETE.equals(event.getEvent())) {
			this.delete = event.getTime();
		}
	}

}