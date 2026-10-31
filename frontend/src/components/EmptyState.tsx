interface EmptyStateProps {
  title: string;
  /** The clear next step shown below the title. */
  description: string;
  /** Optional call-to-action (e.g. "Create your first key"). */
  action?: React.ReactNode;
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="empty-state" role="status">
      <h3 className="empty-state__title">{title}</h3>
      <p className="empty-state__desc">{description}</p>
      {action && <div className="empty-state__action">{action}</div>}
    </div>
  );
}
