interface EmptyStateProps {
  title: string;
  /** The clear next step shown below the title. */
  description: string;
  /** Optional call-to-action (e.g. "Create your first key"). */
  action?: React.ReactNode;
  icon?: string;
}

export function EmptyState({ title, description, action, icon }: EmptyStateProps) {
  return (
    <div className="empty-state" role="status">
      <div className="empty-state__icon" aria-hidden="true">{icon ?? "∅"}</div>
      <h3 className="empty-state__title">{title}</h3>
      <p className="empty-state__desc">{description}</p>
      {action && <div className="empty-state__action">{action}</div>}
    </div>
  );
}
