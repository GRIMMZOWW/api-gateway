/** What the product does, drawn once: three requests reach the gate, two are let through to your API, one is turned away. */
export function GateDiagram() {
  return (
    <svg
      className="gate-diagram"
      viewBox="0 0 480 260"
      role="img"
      aria-label="Three requests reach Gatecraft. Two are let through to your API with a 200. The third is turned away with a 429."
    >
      {[60, 130, 200].map((y, i) => (
        <g key={y}>
          <rect
            className="gd-box"
            x="14"
            y={y - 16}
            width="98"
            height="32"
            rx="4"
          />
          <text className="gd-text" x="63" y={y + 4} textAnchor="middle">
            request {i + 1}
          </text>
        </g>
      ))}

      <line className="gd-line" x1="112" y1="60" x2="222" y2="60" />
      <line className="gd-line" x1="112" y1="130" x2="222" y2="130" />
      <line className="gd-line" x1="112" y1="200" x2="212" y2="200" />
      <line className="gd-stop" x1="212" y1="188" x2="212" y2="212" />
      <line className="gd-pass" x1="234" y1="60" x2="372" y2="60" />
      <line className="gd-pass" x1="234" y1="130" x2="372" y2="130" />

      <rect className="gd-wall" x="222" y="30" width="12" height="14" />
      <rect className="gd-wall" x="222" y="76" width="12" height="38" />
      <rect className="gd-wall" x="222" y="146" width="12" height="86" />
      <circle className="gd-lamp" cx="228" cy="21" r="5" />

      <rect className="gd-box" x="372" y="30" width="94" height="202" rx="4" />
      <text className="gd-text" x="419" y="134" textAnchor="middle">
        your API
      </text>

      <text className="gd-ok" x="303" y="50" textAnchor="middle">
        200
      </text>
      <text className="gd-ok" x="303" y="120" textAnchor="middle">
        200
      </text>
      <text className="gd-no" x="162" y="188" textAnchor="middle">
        429
      </text>
      <text className="gd-text" x="228" y="252" textAnchor="middle">
        Gatecraft
      </text>
    </svg>
  );
}
