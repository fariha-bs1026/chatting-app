function LogoMark({ size = 'default' }) {
  const sizeClass = size === 'large' ? 'large' : size === 'tiny' ? 'tiny' : '';
  return (
    <span className={`logo-mark ${sizeClass}`} aria-hidden="true">
      <svg viewBox="0 0 40 40" role="img">
        <path
          className="logo-bubble"
          d="M10.4 11.6c0-3 2.4-5.4 5.4-5.4h8.4c3 0 5.4 2.4 5.4 5.4v6.8c0 3-2.4 5.4-5.4 5.4h-4.7l-6 5.1c-.9.7-2.2.1-2.2-1v-4.2c-2.7-.3-4.9-2.6-4.9-5.3v-6.8Z"
        />
        <path
          className="logo-heart"
          d="M19.9 18.8c-3.7-2.2-5.5-3.8-5.5-6.1 0-1.5 1.2-2.7 2.8-2.7 1.1 0 2 .5 2.7 1.5.7-1 1.6-1.5 2.7-1.5 1.6 0 2.8 1.2 2.8 2.7 0 2.3-1.8 3.9-5.5 6.1Z"
        />
        <circle className="logo-dot" cx="30.2" cy="29.7" r="3.8" />
      </svg>
    </span>
  );
}

export default LogoMark;
