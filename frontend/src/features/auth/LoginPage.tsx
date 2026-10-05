import { useEffect, useRef, useState } from 'react';
import { DestinationChoice } from './DestinationChoice';
import { DestinationPlaceholder } from './DestinationPlaceholder';
import { FrameSwitch } from './FrameSwitch';
import { IluminuraFrame } from './IluminuraFrame';
import { LeaveDialog } from './LeaveDialog';
import { LoginForm } from './LoginForm';
import { PortalFrame } from './PortalFrame';
import { rememberFrame, storedFrame, type Frame } from './frame';
import { useLogin } from './useLogin';

/**
 * Provisional: `Setting` holds the house's name in the backend, but there is no
 * anonymous endpoint to read it before the login, so it is a constant here.
 */
const BRAND_NAME = 'Hospedaria';

const FRAME_COMPONENTS = {
  iluminura: IluminuraFrame,
  portal: PortalFrame,
} as const;

export function LoginPage() {
  const passwordField = useRef<HTMLInputElement>(null);
  const screen = useLogin(passwordField);
  const [frame, setFrame] = useState<Frame>(storedFrame);

  /**
   * The frame's tokens hang off the root element, not off the frame's own div,
   * so the switch — which lives outside the frame — is painted in the colours of
   * the frame it is switching.
   */
  useEffect(() => {
    document.documentElement.dataset['frame'] = frame;
  }, [frame]);

  const Frame = FRAME_COMPONENTS[frame];

  return (
    <>
      <FrameSwitch
        frame={frame}
        onChange={(chosen) => {
          rememberFrame(chosen);
          setFrame(chosen);
        }}
      />
      <Frame brandName={BRAND_NAME}>
        {screen.stage.kind === 'form' ? (
          <LoginForm screen={screen} passwordField={passwordField} />
        ) : null}
        <DestinationChoice screen={screen} />
        <DestinationPlaceholder screen={screen} />
        <LeaveDialog screen={screen} />
      </Frame>
    </>
  );
}
