import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { usePublicMenu, usePublicTable, useSubmitOrderRequest, type PublicMenuItem } from './customerApi';
import {
  customerDeviceId,
  storeCustomerName,
  storeLastOrderRequestId,
  storedCustomerName,
  storedLastOrderRequestId,
} from './customerIdentity';
import { cartReducer, toOrderLines } from './cart';
import { CUSTOMER, describeCustomerError, hasErrorCode } from './customerMessages';
import { CustomerHeader, CustomerNav, StateMessage, type CustomerScreen } from './CustomerChrome';
import { ArchFrame, WelcomeScreen } from './WelcomeScreen';
import { MenuScreen } from './MenuScreen';
import { ItemSheet } from './ItemSheet';
import { CartScreen, type SendBlock } from './CartScreen';
import { OrdersScreen } from './OrdersScreen';
import '../../styles/customer-table.css';

const NOTICE_MS = 2600;

/** The codes after which the table itself must be read again: its state moved. */
const TABLE_MOVED_CODES = ['TABLE_ORDER_REQUEST_ALREADY_PENDING', 'TAB_CLOSING_REFUSES_ORDER_REQUEST', 'DINING_TABLE_NOT_FOUND'];

/**
 * The customer's phone at the table, opened by the table's QR (decision #3):
 * public, without login. Screens are local state, as in the design; the cart
 * lives only here until it is sent (decision #8).
 */
export function CustomerTablePage() {
  const { orderingToken = '' } = useParams();
  const queryClient = useQueryClient();
  const table = usePublicTable(orderingToken);
  const menu = usePublicMenu();
  const submit = useSubmitOrderRequest(orderingToken);

  const [deviceId] = useState(customerDeviceId);
  const [customerName, setCustomerName] = useState(storedCustomerName);
  const [screen, setScreen] = useState<CustomerScreen>(() => (storedCustomerName() ? 'menu' : 'welcome'));
  const [cart, dispatch] = useReducer(cartReducer, []);
  const [choosing, setChoosing] = useState<PublicMenuItem | null>(null);
  const [lastRequestId, setLastRequestId] = useState(() => storedLastOrderRequestId(orderingToken));
  const [notice, setNotice] = useState<string | null>(null);
  const [sendError, setSendError] = useState<{ message: string; fixName: boolean } | null>(null);
  const sending = useRef(false);

  const tableLabel = table.data?.diningTableLabel ?? '';

  useEffect(() => {
    if (tableLabel) {
      document.title = CUSTOMER.documentTitle(tableLabel);
    }
  }, [tableLabel]);

  useEffect(() => {
    if (!notice) {
      return;
    }
    const timer = window.setTimeout(() => setNotice(null), NOTICE_MS);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const go = useCallback((next: CustomerScreen) => setScreen(next), []);

  const forgetRequest = useCallback(() => {
    storeLastOrderRequestId(orderingToken, null);
    setLastRequestId(null);
  }, [orderingToken]);

  if (hasErrorCode(table.error, 'DINING_TABLE_NOT_FOUND')) {
    return (
      <div className="customer-table">
        <ArchFrame>
          <div className="ct-welcome-table" role="alert">
            <h1 className="ct-welcome-title ct-welcome-title-small">{CUSTOMER.invalidTable.title}</h1>
            <p className="ct-lead">{CUSTOMER.invalidTable.message}</p>
          </div>
        </ArchFrame>
      </div>
    );
  }

  if (!table.data) {
    return (
      <div className="customer-table">
        <ArchFrame>
          {table.isPending ? (
            <StateMessage role="status">{CUSTOMER.welcome.loading}</StateMessage>
          ) : (
            <StateMessage role="alert" onRetry={() => void table.refetch()}>
              {CUSTOMER.welcome.failed} {describeCustomerError(table.error)}
            </StateMessage>
          )}
        </ArchFrame>
      </div>
    );
  }

  if (screen === 'welcome' || customerName === null) {
    return (
      <div className="customer-table">
        <WelcomeScreen
          tableLabel={tableLabel}
          initialName={customerName ?? ''}
          onEnter={(name) => {
            storeCustomerName(name);
            setCustomerName(name);
            setSendError(null);
            go(cart.length > 0 ? 'cart' : 'menu');
          }}
        />
      </div>
    );
  }

  const name = customerName;
  const pendingRequestId = table.data.pendingRequestId;
  const sendBlock: SendBlock = !table.data.acceptsOrders
    ? 'closing'
    : pendingRequestId === null
      ? null
      : pendingRequestId === lastRequestId
        ? 'pending-mine'
        : 'pending-other';

  function handleSend() {
    if (sending.current || cart.length === 0) {
      return;
    }
    sending.current = true;
    setSendError(null);
    submit.mutate(
      { customerName: name, customerDeviceId: deviceId, lines: toOrderLines(cart) },
      {
        onSuccess: (request) => {
          storeLastOrderRequestId(orderingToken, request.id);
          setLastRequestId(request.id);
          dispatch({ type: 'clear' });
          setNotice(CUSTOMER.cart.sent);
          go('orders');
        },
        onError: (error) => {
          setSendError({
            message: describeCustomerError(error),
            fixName: hasErrorCode(error, 'INVALID_CUSTOMER_NAME'),
          });
        },
        onSettled: (_data, error) => {
          sending.current = false;
          if (!error || TABLE_MOVED_CODES.some((code) => hasErrorCode(error, code))) {
            void queryClient.invalidateQueries({ queryKey: ['public-table', orderingToken] });
          }
        },
      },
    );
  }

  const titles: Record<Exclude<CustomerScreen, 'welcome'>, string> = {
    menu: CUSTOMER.titles.menu,
    cart: CUSTOMER.titles.cart,
    orders: CUSTOMER.titles.orders,
  };

  return (
    <div className="customer-table">
      <CustomerHeader
        title={titles[screen]}
        tableLabel={tableLabel}
        {...(screen === 'cart' ? { onBack: () => go('menu') } : {})}
      />

      {screen === 'menu' && (
        <MenuScreen
          menu={menu.data}
          isLoading={menu.isPending}
          error={menu.error}
          onRetry={() => void menu.refetch()}
          cart={cart}
          dispatch={dispatch}
          onChoose={setChoosing}
          onSeeCart={() => go('cart')}
        />
      )}
      {screen === 'cart' && (
        <CartScreen
          cart={cart}
          dispatch={dispatch}
          onChooseMore={() => go('menu')}
          onSend={handleSend}
          isSending={submit.isPending}
          sendBlock={sendBlock}
          sendError={sendError}
          onFixName={() => go('welcome')}
        />
      )}
      {screen === 'orders' && (
        <OrdersScreen
          orderingToken={orderingToken}
          customerDeviceId={deviceId}
          lastRequestId={lastRequestId}
          onForgetRequest={forgetRequest}
        />
      )}

      {screen !== 'cart' && <CustomerNav current={screen} onGo={go} />}

      {choosing && (
        <ItemSheet
          item={choosing}
          onClose={() => setChoosing(null)}
          onAdd={(choice) => {
            dispatch({ type: 'add', item: choosing, ...choice });
            setChoosing(null);
          }}
        />
      )}

      {notice && (
        <div className="ct-toast" role="status">
          {notice}
        </div>
      )}
    </div>
  );
}
