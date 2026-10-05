import { FRAMES, FRAME_LABELS, type Frame } from './frame';

/** Temporary, by decision #1: it exists to let the two directions be compared. */
export function FrameSwitch({
  frame,
  onChange,
}: {
  readonly frame: Frame;
  readonly onChange: (frame: Frame) => void;
}) {
  return (
    <div className="frame-switch">
      <span>Moldura</span>
      <div className="seg" role="radiogroup" aria-label="Moldura da tela de entrada">
        {FRAMES.map((option) => (
          <label className="seg-opt" key={option}>
            <input
              type="radio"
              name="login-frame"
              value={option}
              checked={frame === option}
              onChange={() => onChange(option)}
            />
            {FRAME_LABELS[option]}
          </label>
        ))}
      </div>
    </div>
  );
}
